package com.example.paperReader.ui.paperdetail

import com.example.paperReader.domain.model.Paper
import com.example.paperReader.ui.components.DownloadFeedback

/** The presentations required by the discovery/detail spec: loading, loaded (optionally stale
 * after a failed background refresh), a confirmed not-found result, and a recoverable error when
 * no cached paper exists yet. */
sealed interface PaperDetailContent {
    data object Loading : PaperDetailContent

    data class Loaded(val paper: Paper, val isStale: Boolean = false) : PaperDetailContent

    /**
     * The backend confirmed this paper does not exist (HTTP 404). Stable and terminal: retrying
     * cannot change it, any cached copy is suppressed along with its access actions, and it is
     * deliberately not presented as a transient service failure or as malformed data.
     */
    data object NotFound : PaperDetailContent

    data class Error(val message: String, val isRecoverable: Boolean) : PaperDetailContent
}

data class PaperDetailUiState(
    val content: PaperDetailContent = PaperDetailContent.Loading,
    /** Why the last download attempt could not start; persistent until dismissed. */
    val downloadFeedback: DownloadFeedback? = null,
)
