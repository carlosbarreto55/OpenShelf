package com.example.paperReader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.LibraryRepository
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
) : ViewModel() {

    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)

    private val readerRequests = Channel<String>(Channel.BUFFERED)

    /** Download ids to open the reader on, one per successful Read PDF. */
    val openReaderRequestsFlow = readerRequests.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeSavedPapers(),
        downloadFeedback,
    ) { papers, feedback ->
        LibraryUiState(
            content = if (papers.isEmpty()) LibraryContent.Empty else LibraryContent.Loaded(papers),
            downloadFeedback = feedback,
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

    fun dismissDownloadFeedback() {
        downloadFeedback.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
