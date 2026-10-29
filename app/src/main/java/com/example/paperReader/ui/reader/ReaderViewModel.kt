package com.example.paperReader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.data.reader.PdfOpenResult
import com.example.paperReader.data.reader.PdfPageSource
import com.example.paperReader.di.ApplicationScope
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.ReadingPositionRepository
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.ReadPaperUseCase
import com.example.paperReader.ui.components.DownloadFeedback
import com.example.paperReader.ui.components.OpenFileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * The reader follows one download record (design.md decision 6) and renders by its state: queued or
 * running is progress with cancel, failed is the cause with a retry that confirms again, canceled
 * is a way back, and completed is a file to check before anything renders. Reaching this screen is
 * not an authorisation; the record is.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val downloadsRepository: DownloadsRepository,
    paperRepository: PaperRepository,
    private val readingPositions: ReadingPositionRepository,
    private val readPaper: ReadPaperUseCase,
    private val pageSource: PdfPageSource,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    // Must match `PaperReaderRoute.Reader.downloadId`: typed routes use the property name as the key.
    private val downloadId: String = checkNotNull(savedStateHandle.get<String>("downloadId"))

    private val record: Flow<DownloadRecord?> = downloadsRepository.observeDownloads()
        .map { records -> records.firstOrNull { it.id == downloadId } }

    /** A record's id is its paper's id in both gateways; the record says so when there is one. */
    private val paperId: Flow<PaperId> = record.map { it?.paperId ?: PaperId(downloadId) }.distinctUntilChanged()

    private val paper = paperId.flatMapLatest(paperRepository::observePaper)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val window = MutableStateFlow<ReaderPageWindow?>(null)

    /** The pages rendered for the current view, by page index. */
    val pageImages: StateFlow<Map<Int, PageImage>> = window
        .flatMapLatest { it?.images ?: flowOf(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Opened once per completed file: progress updates and unrelated record writes do not reopen
     * it, so the reader keeps its place while the record changes underneath. */
    private val document: StateFlow<ReaderContent> = record
        .map { it.toPhase() }
        .distinctUntilChanged()
        .transformLatest { phase ->
            if (phase !is Phase.Completed) {
                releaseDocument()
                emit(phase.toContent())
                return@transformLatest
            }
            emit(ReaderContent.Opening)
            emit(open(phase))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderContent.Opening)

    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)
    private val noViewerAvailable = MutableStateFlow(false)

    private val openFileRequests = Channel<OpenFileRequest>(Channel.BUFFERED)
    val openFileRequestsFlow = openFileRequests.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        record.map { it?.title.orEmpty() }.distinctUntilChanged(),
        document,
        paper.map { it?.access is AccessResolution.DirectPdf }.distinctUntilChanged(),
        downloadFeedback,
        noViewerAvailable,
    ) { title, content, accessConfirmed, feedback, noViewer ->
        ReaderUiState(
            title = title,
            content = if (content is ReaderContent.Refused) {
                content.copy(canReadAgain = content.refusal == ReaderRefusal.FILE_MISSING && accessConfirmed)
            } else {
                content
            },
            downloadFeedback = feedback,
            noViewerAvailable = noViewer,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderUiState())

    private val positions = MutableStateFlow<ReadingPosition?>(null)

    init {
        // Written while reading, not only on the way out: a process killed in the background runs
        // no exit path, and the spec requires the position to survive exactly that.
        viewModelScope.launch {
            positions.filterNotNull().distinctUntilChanged().debounce(POSITION_DEBOUNCE_MILLIS).collect { persist(it) }
        }
    }

    fun onViewport(viewport: ReaderViewport) {
        window.value?.update(viewport)
    }

    /** Kept in saved state at once, so a rotation or a process restart comes back here, and in
     * Room after a pause, so a later visit does too. */
    fun onPositionChanged(position: ReadingPosition) {
        savedStateHandle[KEY_PAGE] = position.pageIndex
        savedStateHandle[KEY_OFFSET] = position.pageOffsetFraction
        positions.value = position
    }

    fun onZoomChanged(zoom: Float) {
        savedStateHandle[KEY_ZOOM] = zoom
    }

    /** The screen is leaving: the last position is written now, on a scope that outlives this
     * ViewModel, rather than waiting out a debounce that would be cancelled with it. */
    fun onLeave() {
        val last = positions.value ?: return
        applicationScope.launch { persist(last) }
    }

    /** The download is the user's to cancel; leaving the reader does not. */
    fun cancelTransfer() {
        viewModelScope.launch { downloadsRepository.cancelDownload(downloadId) }
    }

    /** Through [DownloadsRepository.retryDownload], so it is confirmed with the backend again. */
    fun retryTransfer() {
        viewModelScope.launch {
            downloadFeedback.value = null
            when (val result = downloadsRepository.retryDownload(downloadId)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    /** The file is gone: fetch it again, which the reader then follows. Only offered while the
     * paper's access is a confirmed PDF, and [ReadPaperUseCase] checks that again. */
    fun readAgain() {
        val current = paper.value ?: return
        viewModelScope.launch {
            downloadFeedback.value = null
            when (val result = readPaper(current)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    fun openInAnotherApp() {
        viewModelScope.launch {
            val current = record.first() ?: return@launch
            val uri = current.localContentUri ?: return@launch
            noViewerAvailable.value = false
            openFileRequests.send(OpenFileRequest(current.id, uri, current.mimeType))
        }
    }

    fun onNoViewerAvailable() {
        noViewerAvailable.value = true
    }

    fun dismissNoViewerNotice() {
        noViewerAvailable.value = false
    }

    fun dismissDownloadFeedback() {
        downloadFeedback.value = null
    }

    override fun onCleared() {
        releaseDocument()
        pageSource.close()
    }

    private suspend fun open(phase: Phase.Completed): ReaderContent {
        return when (val result = pageSource.open(phase.contentUri)) {
            is PdfOpenResult.Readable -> {
                releaseDocument()
                window.value = ReaderPageWindow(result.pages, viewModelScope, pageSource::render)
                ReaderContent.Reading(
                    pages = result.pages,
                    initialPosition = startingPosition(phase.paperId, result.pageCount),
                    initialZoom = (savedStateHandle.get<Float>(KEY_ZOOM) ?: 1f).coerceIn(MIN_ZOOM, MAX_ZOOM),
                )
            }
            PdfOpenResult.Missing -> refused(ReaderRefusal.FILE_MISSING)
            // Every completed download in an HTTP build passed PDF validation, so a file that is
            // not one, recorded as something other than a PDF, is the demo placeholder.
            PdfOpenResult.NotPdf -> refused(
                if (phase.mimeType == PDF_MIME_TYPE) ReaderRefusal.NOT_A_PDF else ReaderRefusal.DEMO_PLACEHOLDER,
            )
            PdfOpenResult.Encrypted -> refused(ReaderRefusal.ENCRYPTED)
            PdfOpenResult.Malformed -> refused(ReaderRefusal.MALFORMED)
        }
    }

    /** Saved state first — it is this visit's, surviving rotation and process death — then the
     * stored position from an earlier visit, then the first page. */
    private suspend fun startingPosition(paperId: PaperId, pageCount: Int): ReadingPosition {
        val saved = savedStateHandle.get<Int>(KEY_PAGE)?.let { page ->
            runCatching { ReadingPosition(page, savedStateHandle.get<Float>(KEY_OFFSET) ?: 0f) }.getOrNull()
        }
        val position = saved ?: readingPositions.position(paperId) ?: ReadingPosition.START
        return if (position.pageIndex < pageCount) position else ReadingPosition.START
    }

    private suspend fun persist(position: ReadingPosition) {
        val current = document.value as? ReaderContent.Reading ?: return
        if (position.pageIndex >= current.pages.size) return
        readingPositions.save(paperId.first(), position)
    }

    private fun releaseDocument() {
        window.value?.clear()
        window.value = null
    }

    private fun refused(refusal: ReaderRefusal) = ReaderContent.Refused(
        refusal = refusal,
        canReadAgain = false, // decided against the paper's live access in uiState
        canOpenExternally = refusal.fileExists,
    )

    /** The record reduced to what the reader shows, so progress ticks are the only churn. */
    private sealed interface Phase {
        data object NoRecord : Phase
        data class Transfer(val queued: Boolean, val progress: Float?) : Phase
        data class Failed(val errorMessage: String?) : Phase
        data object Canceled : Phase
        data class Completed(val paperId: PaperId, val contentUri: String, val mimeType: String?) : Phase
    }

    private fun DownloadRecord?.toPhase(): Phase {
        val record = this ?: return Phase.NoRecord
        return when (record.state) {
            DownloadState.QUEUED -> Phase.Transfer(queued = true, progress = null)
            DownloadState.RUNNING -> Phase.Transfer(queued = false, progress = record.progressFraction())
            DownloadState.FAILED -> Phase.Failed(record.errorMessage)
            DownloadState.CANCELED -> Phase.Canceled
            DownloadState.COMPLETED -> record.localContentUri
                ?.let { Phase.Completed(record.paperId, it, record.mimeType) }
                ?: Phase.NoRecord
        }
    }

    private fun Phase.toContent(): ReaderContent = when (this) {
        // The route is only navigated to once a record exists, so its absence is a file that is
        // not there, not a state to wait in.
        Phase.NoRecord -> refused(ReaderRefusal.FILE_MISSING)
        is Phase.Transfer -> ReaderContent.Transferring(queued, progress)
        is Phase.Failed -> ReaderContent.TransferFailed(errorMessage)
        Phase.Canceled -> ReaderContent.Canceled
        is Phase.Completed -> ReaderContent.Opening
    }

    private fun DownloadRecord.progressFraction(): Float? {
        val total = progress?.totalBytes?.takeIf { it > 0 } ?: return null
        return (progress.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
        const val POSITION_DEBOUNCE_MILLIS = 1_000L
        private const val PDF_MIME_TYPE = "application/pdf"
        private const val KEY_PAGE = "readerPage"
        private const val KEY_OFFSET = "readerPageOffset"
        private const val KEY_ZOOM = "readerZoom"
    }
}
