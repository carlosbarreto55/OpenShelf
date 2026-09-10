package com.example.paperReader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.RepositoryResult
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

data class OpenFileRequest(val downloadId: String, val contentUri: String, val mimeType: String?)

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
        DownloadsUiState(
            content = if (downloads.isEmpty()) DownloadsContent.Empty else DownloadsContent.Loaded(downloads),
            noViewerAvailableForDownloadId = noViewerId,
            downloadFeedback = feedback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DownloadsUiState())

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
    }
}
