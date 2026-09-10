package com.example.paperReader.ui.downloads

import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.ui.components.DownloadFeedback

sealed interface DownloadsContent {
    data object Loading : DownloadsContent

    data object Empty : DownloadsContent

    data class Loaded(val downloads: List<DownloadRecord>) : DownloadsContent
}

data class DownloadsUiState(
    val content: DownloadsContent = DownloadsContent.Loading,
    /** Set after an attempt to open a completed download found no compatible viewer; persistent
     * (per design.md decision 8) rather than a transient one-off effect, so it survives
     * recomposition until the user dismisses it. */
    val noViewerAvailableForDownloadId: String? = null,
    /** Why the last retry could not be re-confirmed with the backend; persistent until dismissed. */
    val downloadFeedback: DownloadFeedback? = null,
)
