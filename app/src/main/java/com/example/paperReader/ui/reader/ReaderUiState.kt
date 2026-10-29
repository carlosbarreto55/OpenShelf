package com.example.paperReader.ui.reader

import androidx.annotation.StringRes
import com.example.paperReader.R
import com.example.paperReader.data.reader.PageSize
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.ui.components.DownloadFeedback

/**
 * Why the reader will not present a file (design.md decision 5). Each says what is wrong in its own
 * words; none of them borrows a cause from the repository error taxonomy, because none of them is a
 * failure to reach anything — the file is here, or it is not, and it can be read, or it cannot.
 */
enum class ReaderRefusal(@param:StringRes val headlineRes: Int, @param:StringRes val messageRes: Int) {
    /** The record says completed, but the file is gone — or there is no record at all. */
    FILE_MISSING(R.string.reader_refusal_missing_title, R.string.reader_refusal_missing_message),

    /** The demo flavor's download: a disclaimer text file, never presented as the paper. */
    DEMO_PLACEHOLDER(R.string.reader_refusal_demo_title, R.string.reader_refusal_demo_message),

    NOT_A_PDF(R.string.reader_refusal_not_pdf_title, R.string.reader_refusal_not_pdf_message),

    ENCRYPTED(R.string.reader_refusal_encrypted_title, R.string.reader_refusal_encrypted_message),

    MALFORMED(R.string.reader_refusal_malformed_title, R.string.reader_refusal_malformed_message),
    ;

    /** Every refusal but a missing file still has a file another app might open. */
    val fileExists: Boolean get() = this != FILE_MISSING
}

/** The reader follows its download record; this is the record's state as the reader shows it. */
sealed interface ReaderContent {
    /** Waiting for the record, or classifying a completed file before anything renders. */
    data object Opening : ReaderContent

    /** [progress] is the completed fraction, or null when the size is not known yet. */
    data class Transferring(val queued: Boolean, val progress: Float?) : ReaderContent

    /** No partial content is ever shown here: the reader renders only a complete, validated file. */
    data class TransferFailed(val errorMessage: String?) : ReaderContent

    data object Canceled : ReaderContent

    /**
     * [canReadAgain] only when the file is missing *and* the paper's access is still a confirmed
     * PDF, because reading again downloads it. [canOpenExternally] only when there is a file.
     */
    data class Refused(
        val refusal: ReaderRefusal,
        val canReadAgain: Boolean,
        val canOpenExternally: Boolean,
    ) : ReaderContent

    /** Opened, with where to start and at what zoom. Pages themselves arrive separately. */
    data class Reading(
        val pages: List<PageSize>,
        val initialPosition: ReadingPosition,
        val initialZoom: Float,
    ) : ReaderContent
}

data class ReaderUiState(
    val title: String = "",
    val content: ReaderContent = ReaderContent.Opening,
    /** Why a retry or a read-again could not be confirmed with the backend; persistent until
     * dismissed, like the same notice on every other screen. */
    val downloadFeedback: DownloadFeedback? = null,
    /** The external handoff found no app to open the file. */
    val noViewerAvailable: Boolean = false,
)
