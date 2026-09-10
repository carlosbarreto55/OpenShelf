package com.example.paperReader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val toggleSavedPaper: ToggleSavedPaperUseCase,
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = libraryRepository.observeSavedPapers()
        .map { papers ->
            LibraryUiState(
                content = if (papers.isEmpty()) LibraryContent.Empty else LibraryContent.Loaded(papers),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState())

    fun toggleSaved(paper: Paper) {
        viewModelScope.launch { toggleSavedPaper(paper) }
    }

    fun startDownload(paper: Paper) {
        viewModelScope.launch { downloadsRepository.startDownload(paper) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
