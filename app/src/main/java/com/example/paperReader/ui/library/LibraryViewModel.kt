package com.example.paperReader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.ui.components.DownloadFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val toggleSavedPaper: ToggleSavedPaperUseCase,
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)

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

    fun startDownload(paper: Paper) {
        viewModelScope.launch {
            downloadFeedback.value = null
            // Every transfer is re-confirmed with the backend, so a download started from a saved
            // paper can fail for reasons the user needs to hear — dropping the outcome here would
            // leave the action looking like it had succeeded.
            when (val result = downloadsRepository.startDownload(paper)) {
                is RepositoryResult.Success -> Unit
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
