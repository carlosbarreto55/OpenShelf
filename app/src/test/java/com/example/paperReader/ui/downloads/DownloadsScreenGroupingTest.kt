package com.example.paperReader.ui.downloads

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.R
import com.example.paperReader.domain.model.DownloadFailureReason
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the grouped Downloads screen actually puts on screen: headings only where there are
 * records, failures named by cause, and a clear-completed control that appears only when there is
 * something to clear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2400dp")
class DownloadsScreenGroupingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun mixedStatesAppearUnderTheirOwnHeadingsInOrder() {
        setContent(
            listOf(
                DownloadSection(DownloadGroup.IN_PROGRESS, listOf(record("running", DownloadState.RUNNING))),
                DownloadSection(DownloadGroup.NEEDS_ATTENTION, listOf(record("failed", DownloadState.FAILED))),
                DownloadSection(DownloadGroup.ON_THIS_DEVICE, listOf(record("done", DownloadState.COMPLETED))),
            ),
        )

        val inProgress = heading(R.string.downloads_group_in_progress)
        val needsAttention = heading(R.string.downloads_group_needs_attention)
        val onThisDevice = heading(R.string.downloads_group_on_this_device)

        // Reading order, top to bottom: what is moving, what needs a decision, then what is done.
        assertTrue(inProgress < needsAttention)
        assertTrue(needsAttention < onThisDevice)
    }

    @Test
    fun aSingleStateRendersOneHeadingAndNoEmptyOnes() {
        setContent(
            listOf(
                DownloadSection(DownloadGroup.ON_THIS_DEVICE, listOf(record("done", DownloadState.COMPLETED))),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.downloads_group_on_this_device)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloads_group_in_progress)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.downloads_group_needs_attention)).assertDoesNotExist()
    }

    @Test
    fun anInvalidPdfFailureNamesItsCauseAndSaysNoPartialFileWasKept() {
        setContent(
            listOf(
                DownloadSection(
                    DownloadGroup.NEEDS_ATTENTION,
                    listOf(
                        record(
                            "failed",
                            DownloadState.FAILED,
                            errorMessage = DownloadFailureReason.INVALID_PDF.message,
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.downloads_failure_heading_invalid_pdf)).assertIsDisplayed()
        composeRule.onNodeWithText(DownloadFailureReason.INVALID_PDF.message).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloads_failure_no_partial_file)).assertIsDisplayed()
    }

    @Test
    fun aTransportFailureFallsBackToAGenericHeadingRatherThanInventingACause() {
        setContent(
            listOf(
                DownloadSection(
                    DownloadGroup.NEEDS_ATTENTION,
                    listOf(record("failed", DownloadState.FAILED, errorMessage = "Connection reset")),
                ),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.downloads_failure_heading_generic)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloads_failure_heading_invalid_pdf)).assertDoesNotExist()
        composeRule.onNodeWithText("Connection reset").assertIsDisplayed()
    }

    @Test
    fun clearCompletedIsOfferedOnlyWhenSomethingIsCompleted() {
        var cleared = 0
        setContent(
            listOf(
                DownloadSection(DownloadGroup.ON_THIS_DEVICE, listOf(record("done", DownloadState.COMPLETED))),
            ),
            onClearCompleted = { cleared++ },
        )

        composeRule.onNodeWithText(context.getString(R.string.downloads_action_clear_completed)).performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun clearCompletedIsAbsentWhenNothingIsCompleted() {
        setContent(
            listOf(
                DownloadSection(DownloadGroup.NEEDS_ATTENTION, listOf(record("failed", DownloadState.FAILED))),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.downloads_action_clear_completed)).assertDoesNotExist()
    }

    @Test
    fun noDownloadsAtAllShowsTheEmptyStateWithoutAnyHeadings() {
        composeRule.setContent {
            PaperReaderTheme {
                DownloadsScreen(
                    state = DownloadsUiState(content = DownloadsContent.Empty),
                    onRetry = {},
                    onCancel = {},
                    onOpen = {},
                    onDismissNoViewerNotice = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.downloads_empty_title)).assertIsDisplayed()
        DownloadGroup.entries.forEach { group ->
            composeRule.onNodeWithText(context.getString(group.headingRes)).assertDoesNotExist()
        }
    }

    private fun heading(resId: Int): Float =
        composeRule.onNodeWithText(context.getString(resId)).getUnclippedBoundsInRoot().top.value

    private fun setContent(
        sections: List<DownloadSection>,
        onClearCompleted: () -> Unit = {},
    ) {
        composeRule.setContent {
            PaperReaderTheme {
                DownloadsScreen(
                    state = DownloadsUiState(content = DownloadsContent.Loaded(sections)),
                    onRetry = {},
                    onCancel = {},
                    onOpen = {},
                    onDismissNoViewerNotice = {},
                    onClearCompleted = onClearCompleted,
                )
            }
        }
    }

    private fun record(
        id: String,
        state: DownloadState,
        errorMessage: String? = null,
    ) = DownloadRecord(
        id = id,
        paperId = PaperId("W1"),
        title = "Paper $id",
        state = state,
        localContentUri = if (state == DownloadState.COMPLETED) "content://demo/$id" else null,
        mimeType = if (state == DownloadState.COMPLETED) "application/pdf" else null,
        errorMessage = errorMessage,
        updatedAtEpochMillis = 0L,
    )
}
