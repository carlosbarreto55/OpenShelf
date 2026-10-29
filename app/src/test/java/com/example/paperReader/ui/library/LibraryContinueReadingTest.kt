package com.example.paperReader.ui.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.model.ResumablePaper
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryContinueReadingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val continued = mutableListOf<ResumablePaper>()

    private fun show(state: LibraryUiState) {
        composeRule.setContent {
            PaperReaderTheme {
                LibraryScreen(
                    state = state,
                    onOpenPaper = {},
                    onToggleSave = {},
                    onReadPdf = {},
                    onReadOnline = {},
                    onContinueReading = { continued += it },
                )
            }
        }
    }

    @Test
    fun aResumablePaperShowsTheOneBasedPageAndOpensTheReader() {
        val paper = ResumablePaper(
            paperId = PaperId("W1"),
            downloadId = "download-W1",
            title = "Harnessing Generative AI",
            position = ReadingPosition(pageIndex = 3, pageOffsetFraction = 0.5f),
            lastReadAtEpochMillis = 100,
        )
        show(LibraryUiState(content = LibraryContent.Empty, continueReading = listOf(paper)))

        composeRule.onNodeWithText("Continue reading").assertExists()
        composeRule.onNodeWithText("Page 4").assertExists()
        // Reading a paper does not save it, so the saved list keeps its own heading and empty state.
        composeRule.onNodeWithText("Saved papers").assertExists()
        composeRule.onNodeWithText("Your library is empty").assertExists()

        composeRule.onNodeWithText("Harnessing Generative AI").performClick()

        assertEquals(listOf(paper), continued)
    }

    @Test
    fun withNothingToResumeTheLibraryLooksAsItDidBefore() {
        show(LibraryUiState(content = LibraryContent.Empty))

        composeRule.onNodeWithText("Continue reading").assertDoesNotExist()
        composeRule.onNodeWithText("Saved papers").assertDoesNotExist()
    }
}
