package com.example.paperReader.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.paperReader.data.reader.PageSize
import com.example.paperReader.domain.model.DownloadFailureReason
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Go-to-page is covered on a device instead (`androidTest`'s `ReaderGoToPageTest`): a text field
 * inside a dialog window never lets Robolectric's Compose rule reach idle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var cancels = 0
    private var retries = 0
    private var readAgains = 0
    private var handoffs = 0
    private val positions = mutableListOf<ReadingPosition>()
    private val zooms = mutableListOf<Float>()

    private fun show(content: ReaderContent, images: Map<Int, PageImage> = emptyMap()) {
        composeRule.setContent {
            PaperReaderTheme {
                ReaderScreen(
                    state = ReaderUiState(title = "Demo title", content = content),
                    pageImages = images,
                    onBack = {},
                    onCancelTransfer = { cancels++ },
                    onRetryTransfer = { retries++ },
                    onReadAgain = { readAgains++ },
                    onOpenInAnotherApp = { handoffs++ },
                    onPositionChanged = { positions += it },
                    onZoomChanged = { zooms += it },
                )
            }
        }
    }

    private fun reading(pages: Int = 3, at: ReadingPosition = ReadingPosition.START) =
        ReaderContent.Reading(List(pages) { PageSize(612, 792) }, initialPosition = at, initialZoom = 1f)

    @Test
    fun aTransferShowsItsProgressAndCanBeCancelled() {
        show(ReaderContent.Transferring(queued = false, progress = 0.4f))

        composeRule.onNodeWithText("40% downloaded").assertExists()
        composeRule.onNodeWithContentDescription("Downloading paper").assertExists()
        composeRule.onNodeWithText("Cancel download").performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun aFailedTransferNamesItsCauseAndOffersARetry() {
        show(ReaderContent.TransferFailed(DownloadFailureReason.INVALID_PDF.message))

        composeRule.onNodeWithText("Not a valid PDF").assertExists()
        composeRule.onNodeWithText("No partial file was kept.").assertExists()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun theDemoPlaceholderIsRefusedAsDemoOutputWithTheHandoffStillOffered() {
        show(ReaderContent.Refused(ReaderRefusal.DEMO_PLACEHOLDER, canReadAgain = false, canOpenExternally = true))

        composeRule.onNodeWithText("This is demo output, not the paper").assertExists()
        composeRule.onNodeWithText("Download again").assertDoesNotExist()
        composeRule.onNodeWithText("Open in another app").performClick()

        assertEquals(1, handoffs)
    }

    @Test
    fun aMissingFileOffersReadingAgainAndNoHandoff() {
        show(ReaderContent.Refused(ReaderRefusal.FILE_MISSING, canReadAgain = true, canOpenExternally = false))

        composeRule.onNodeWithText("This paper isn't on the device any more").assertExists()
        composeRule.onNodeWithText("Open in another app").assertDoesNotExist()
        composeRule.onNodeWithText("Download again").performClick()

        assertEquals(1, readAgains)
    }

    @Test
    fun aMissingFileWhoseAccessIsNoLongerConfirmedOffersNothing() {
        show(ReaderContent.Refused(ReaderRefusal.FILE_MISSING, canReadAgain = false, canOpenExternally = false))

        composeRule.onNodeWithText("Download again").assertDoesNotExist()
        composeRule.onNodeWithText("Open in another app").assertDoesNotExist()
    }

    @Test
    fun theReaderReportsThePageAgainstTheTotalAndMovesBetweenPages() {
        show(reading(pages = 3))
        composeRule.onNodeWithText("Page 1 of 3").assertExists()
        composeRule.onNodeWithContentDescription("Previous page").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Next page").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Page 2 of 3").assertExists()
        assertEquals(1, positions.last().pageIndex)
    }

    @Test
    fun aStoredPositionIsWhereTheReaderOpens() {
        show(reading(pages = 5, at = ReadingPosition(pageIndex = 3, pageOffsetFraction = 0f)))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Page 4 of 5").assertExists()
    }

    @Test
    fun aPageThatFailedToRenderSaysSoInsteadOfShowingBlank() {
        show(reading(pages = 3), images = mapOf(0 to PageImage.Unavailable(1000)))

        composeRule.onNodeWithText("Page 1 couldn't be shown").assertExists()
    }

    @Test
    fun everyPageIsLabelledForScreenReaders() {
        show(reading(pages = 3))

        composeRule.onNodeWithContentDescription("Page 1 of 3").assertExists()
    }

    @Test
    fun zoomIsReachableWithoutAPinch() {
        show(reading(pages = 3))
        val document = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        document.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))

        val zoomIn = document.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Zoom in" }
        composeRule.runOnIdle { zoomIn.action() }
        composeRule.waitForIdle()

        assertEquals(listOf(1.5f), zooms)
        document.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 150%"))
    }

    @Test
    fun theDocumentAppearsInPlaceOfProgressWhenTheTransferCompletes() {
        var content by mutableStateOf<ReaderContent>(ReaderContent.Transferring(queued = false, progress = 0.9f))
        composeRule.setContent {
            PaperReaderTheme {
                ReaderScreen(
                    state = ReaderUiState(title = "Demo title", content = content),
                    pageImages = emptyMap(),
                    onBack = {},
                    onCancelTransfer = {},
                    onRetryTransfer = {},
                    onReadAgain = {},
                    onOpenInAnotherApp = {},
                )
            }
        }
        composeRule.onNodeWithText("90% downloaded").assertExists()

        content = reading(pages = 2)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("90% downloaded").assertDoesNotExist()
        composeRule.onNodeWithText("Page 1 of 2").assertExists()
    }
}
