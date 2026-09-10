package com.example.paperReader.ui.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Rule
import org.junit.Test

/** Covers the download-lifecycle action wiring (cancel/retry/open) and the placeholder-file
 * disclaimer required by the paper-downloads spec's honest-placeholder-behavior requirement. */
class DownloadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runningDownloadOffersCancel() {
        var canceled = false
        setContentWith(record(state = DownloadState.RUNNING), onCancel = { canceled = true })

        composeRule.onNodeWithText("Cancel").performClick()

        assert(canceled)
    }

    @Test
    fun failedDownloadOffersRetry() {
        var retried = false
        setContentWith(record(state = DownloadState.FAILED, errorMessage = "Network error"), onRetry = { retried = true })

        composeRule.onNodeWithText("Retry").performClick()

        assert(retried)
        composeRule.onNodeWithText("Network error").assertIsDisplayed()
    }

    @Test
    fun completedPlaceholderShowsDemoNoticeAndOpenAction() {
        var opened = false
        setContentWith(
            record(state = DownloadState.COMPLETED, localContentUri = "content://demo/1", mimeType = "text/plain"),
            onOpen = { opened = true },
        )

        composeRule.onNodeWithText("Demo placeholder — not a real PDF.").assertIsDisplayed()
        composeRule.onNodeWithText("Open").performClick()

        assert(opened)
    }

    @Test
    fun completedRealPdfShowsNoDemoNotice() {
        setContentWith(record(state = DownloadState.COMPLETED, localContentUri = "content://demo/1", mimeType = "application/pdf"))

        composeRule.onNodeWithText("Demo placeholder — not a real PDF.").assertDoesNotExist()
    }

    private fun setContentWith(
        record: DownloadRecord,
        onRetry: (String) -> Unit = {},
        onCancel: (String) -> Unit = {},
        onOpen: (DownloadRecord) -> Unit = {},
    ) {
        composeRule.setContent {
            PaperReaderTheme {
                DownloadsScreen(
                    state = DownloadsUiState(content = DownloadsContent.Loaded(listOf(record))),
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onOpen = onOpen,
                    onDismissNoViewerNotice = {},
                )
            }
        }
    }

    private fun record(
        state: DownloadState,
        localContentUri: String? = null,
        mimeType: String? = null,
        errorMessage: String? = null,
    ) = DownloadRecord(
        id = "d1",
        paperId = PaperId("W2741809807"),
        title = "Test paper",
        state = state,
        localContentUri = localContentUri,
        mimeType = mimeType,
        errorMessage = errorMessage,
        updatedAtEpochMillis = 0L,
    )
}
