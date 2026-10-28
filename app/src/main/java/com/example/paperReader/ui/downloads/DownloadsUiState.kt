package com.example.paperReader.ui.downloads

import androidx.annotation.StringRes
import com.example.paperReader.R
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.ui.components.DownloadFeedback

/**
 * Downloads grouped by what the user has to do with them, not by the internal state name
 * (design.md decision 6). The declaration order here is the order on screen: anything still moving
 * or still waiting on a decision comes before files that are simply present.
 *
 * There is deliberately no group for canceled records — a cancellation is a decision the user
 * already made, so the record leaves the list rather than becoming a category.
 */
enum class DownloadGroup(@param:StringRes val headingRes: Int) {
    IN_PROGRESS(R.string.downloads_group_in_progress),
    NEEDS_ATTENTION(R.string.downloads_group_needs_attention),
    ON_THIS_DEVICE(R.string.downloads_group_on_this_device),
}

/** A group that actually has records. An empty group is never emitted, so no screen has to decide
 * whether to render a heading with nothing under it. */
data class DownloadSection(val group: DownloadGroup, val records: List<DownloadRecord>)

sealed interface DownloadsContent {
    data object Loading : DownloadsContent

    data object Empty : DownloadsContent

    data class Loaded(val sections: List<DownloadSection>) : DownloadsContent
}

data class DownloadsUiState(
    val content: DownloadsContent = DownloadsContent.Loading,
    /** Set after an attempt to open a completed download found no compatible viewer; persistent
     * (per design.md decision 8) rather than a transient one-off effect, so it survives
     * recomposition until the user dismisses it. */
    val noViewerAvailableForDownloadId: String? = null,
    /** Why the last retry could not be re-confirmed with the backend; persistent until dismissed. */
    val downloadFeedback: DownloadFeedback? = null,
) {
    /** Whether "clear completed" has anything to act on. */
    val hasCompletedDownloads: Boolean
        get() = (content as? DownloadsContent.Loaded)
            ?.sections
            ?.any { it.group == DownloadGroup.ON_THIS_DEVICE }
            ?: false
}
