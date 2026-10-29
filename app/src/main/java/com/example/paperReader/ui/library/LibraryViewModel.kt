package com.example.paperReader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.ResumablePaper
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.repository.ReadingPositionRepository
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.ReadPaperUseCase
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.ui.components.DownloadFeedback
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
class LibraryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val toggleSavedPaper: ToggleSavedPaperUseCase,
    private val readPaper: ReadPaperUseCase,
    readingPositions: ReadingPositionRepository,
) : ViewModel() {

    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)

    private val readerRequests = Channel<String>(Channel.BUFFERED)

    /** Download ids to open the reader on, one per successful Read PDF or continued paper. */
    val openReaderRequestsFlow = readerRequests.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeSavedPapers(),
        downloadFeedback,
        readingPositions.observeResumable(),
    ) { papers, feedback, resumable ->
        LibraryUiState(
            content = if (papers.isEmpty()) LibraryContent.Empty else LibraryContent.Loaded(papers),
            downloadFeedback = feedback,
            continueReading = resumable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState())

    fun toggleSaved(paper: Paper) {
        viewModelScope.launch { toggleSavedPaper(paper) }
    }

    fun readPdf(paper: Paper) {
        viewModelScope.launch {
            downloadFeedback.value = null
            // Read PDF may have to confirm with the backend before a transfer, so it can fail for
            // reasons the user needs to hear: no PDF, not found, offline, timeout. It opens the
            // reader only once there is a download record to open it on.
            when (val result = readPaper(paper)) {
                is RepositoryResult.Success -> readerRequests.send(result.value)
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    /**
     * Picks a paper up where it was left. This opens a file already on the device rather than
     * answering an access action, so like Downloads it does not ask whether access is still a
     * confirmed PDF; the reader itself checks the record and the file.
     */
    fun continueReading(paper: ResumablePaper) {
        viewModelScope.launch { readerRequests.send(paper.downloadId) }
    }

    fun dismissDownloadFeedback() {
        downloadFeedback.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
