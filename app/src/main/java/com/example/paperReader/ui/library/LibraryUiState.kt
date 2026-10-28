package com.example.paperReader.ui.library

import com.example.paperReader.domain.model.Paper
import com.example.paperReader.ui.components.DownloadFeedback

sealed interface LibraryContent {
    data object Loading : LibraryContent

    data object Empty : LibraryContent

    data class Loaded(val papers: List<Paper>) : LibraryContent
}

data class LibraryUiState(
    val content: LibraryContent = LibraryContent.Loading,
    /**
     * Why the last download started from the library could not be confirmed. The saved-paper list
     * itself is read from Room and cannot fail, so this — not a dead content-level error state — is
     * the failure the library actually has to report, and it reports it through the same
     * cause-named surface search and detail use.
     */
    val downloadFeedback: DownloadFeedback? = null,
)
