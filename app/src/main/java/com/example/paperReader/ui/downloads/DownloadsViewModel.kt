package com.example.paperReader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.ui.components.DownloadFeedback
import com.example.paperReader.ui.components.OpenFileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    private val noViewerAvailableForDownloadId = MutableStateFlow<String?>(null)
    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)

    private val openFileRequests = Channel<OpenFileRequest>(Channel.BUFFERED)
    val openFileRequestsFlow = openFileRequests.receiveAsFlow()

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloadsRepository.observeDownloads(),
        noViewerAvailableForDownloadId,
        downloadFeedback,
    ) { downloads, noViewerId, feedback ->
        val sections = sectionsOf(downloads)
        DownloadsUiState(
            content = if (sections.isEmpty()) DownloadsContent.Empty else DownloadsContent.Loaded(sections),
            noViewerAvailableForDownloadId = noViewerId,
            downloadFeedback = feedback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DownloadsUiState())

    fun clearCompleted() {
        viewModelScope.launch {
            downloadFeedback.value = null
            // The repository owns the record and the file together; a record without its file (or
            // the reverse) is the leak this control exists to prevent.
            when (val result = downloadsRepository.clearCompletedDownloads()) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    fun retry(downloadId: String) {
        viewModelScope.launch {
            downloadFeedback.value = null
            // Retry re-confirms the descriptor with the backend on every attempt, so it can fail
            // even though the record already exists.
            when (val result = downloadsRepository.retryDownload(downloadId)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    fun dismissDownloadFeedback() {
        downloadFeedback.value = null
    }

    fun cancel(downloadId: String) {
        viewModelScope.launch { downloadsRepository.cancelDownload(downloadId) }
    }

    fun open(record: DownloadRecord) {
        val uri = record.localContentUri ?: return
        noViewerAvailableForDownloadId.value = null
        viewModelScope.launch { openFileRequests.send(OpenFileRequest(record.id, uri, record.mimeType)) }
    }

    fun onNoViewerAvailable(downloadId: String) {
        noViewerAvailableForDownloadId.value = downloadId
    }

    fun dismissNoViewerNotice() {
        noViewerAvailableForDownloadId.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Ordering is decided here rather than inherited from the DAO, so it is deterministic and
         * testable: groups come out in [DownloadGroup] declaration order, and within a group the
         * most recently touched record comes first. Canceled records are dropped entirely.
         */
        fun sectionsOf(downloads: List<DownloadRecord>): List<DownloadSection> {
            val grouped = downloads
                .mapNotNull { record -> record.state.group()?.let { it to record } }
                .groupBy({ it.first }, { it.second })

            return DownloadGroup.entries.mapNotNull { group ->
                grouped[group]
                    ?.takeIf { it.isNotEmpty() }
                    ?.sortedByDescending { it.updatedAtEpochMillis }
                    ?.let { DownloadSection(group, it) }
            }
        }

        fun DownloadState.group(): DownloadGroup? = when (this) {
            DownloadState.QUEUED, DownloadState.RUNNING -> DownloadGroup.IN_PROGRESS
            DownloadState.FAILED -> DownloadGroup.NEEDS_ATTENTION
            DownloadState.COMPLETED -> DownloadGroup.ON_THIS_DEVICE
            // Not a group: the user already decided, so the record leaves the list.
            DownloadState.CANCELED -> null
        }
    }
}
