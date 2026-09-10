package com.example.paperReader.ui.paperdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.RepositoryError
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
class PaperDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PaperRepository,
    private val toggleSavedPaper: ToggleSavedPaperUseCase,
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    // Navigation-Compose's typed routes populate the SavedStateHandle using the route's property
    // name as the argument key, so this must stay in sync with `PaperReaderRoute.PaperDetail.paperId`.
    private val paperId = PaperId(checkNotNull(savedStateHandle.get<String>("paperId")))
    private val networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Loading)
    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)

    val uiState: StateFlow<PaperDetailUiState> = combine(
        repository.observePaper(paperId),
        networkStatus,
        downloadFeedback,
    ) { paper, status, feedback ->
        PaperDetailUiState(content = toContent(paper, status), downloadFeedback = feedback)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PaperDetailUiState())

    init {
        refresh()
    }

    fun retry() {
        refresh()
    }

    fun toggleSaved() {
        val paper = (uiState.value.content as? PaperDetailContent.Loaded)?.paper ?: return
        viewModelScope.launch { toggleSavedPaper(paper) }
    }

    fun startDownload() {
        val paper = (uiState.value.content as? PaperDetailContent.Loaded)?.paper ?: return
        viewModelScope.launch {
            downloadFeedback.value = null
            when (val result = downloadsRepository.startDownload(paper)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    fun dismissDownloadFeedback() {
        downloadFeedback.value = null
    }

    private fun refresh() {
        viewModelScope.launch {
            networkStatus.value = NetworkStatus.Loading
            networkStatus.value = when (val result = repository.refreshPaper(paperId)) {
                is RepositoryResult.Success -> NetworkStatus.Idle
                is RepositoryResult.Failure -> NetworkStatus.Error(result.error)
            }
        }
    }

    private fun toContent(paper: Paper?, status: NetworkStatus): PaperDetailContent {
        // Not-found wins over any cached copy: the backend has confirmed the paper is gone, so
        // continuing to show it (and its access actions) would be showing something that no
        // longer exists.
        if (status is NetworkStatus.Error && status.error == RepositoryError.NotFound) {
            return PaperDetailContent.NotFound
        }
        return when {
            paper != null -> PaperDetailContent.Loaded(paper = paper, isStale = status is NetworkStatus.Error)
            status is NetworkStatus.Error -> PaperDetailContent.Error(status.error.userMessage, status.error.isRecoverable)
            else -> PaperDetailContent.Loading
        }
    }

    private sealed interface NetworkStatus {
        data object Loading : NetworkStatus

        data object Idle : NetworkStatus

        data class Error(val error: RepositoryError) : NetworkStatus
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
