package com.example.paperReader.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Rule
import org.junit.Test

/** Same verified-access rule as [com.example.paperReader.ui.components.PaperCardAccessSemanticsTest],
 * exercised through the real [LibraryScreen] composable: the verified-paper-access spec's "Inaccessible
 * paper is saved" scenario requires the library view to expose no access affordance for a saved paper
 * whose access is inconclusive or unavailable. */
class LibraryScreenAccessSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedPaperWithConfirmedPdfShowsDownloadAction() {
        val paper = paperWith(
            AccessResolution.DirectPdf(ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W1.pdf")),
        )
        setContentWith(listOf(paper))

        composeRule.onNodeWithText("Download PDF").assertIsDisplayed()
    }

    @Test
    fun savedInaccessiblePapersExposeNoAccessAction() {
        val silentPapers = listOf(
            paperWith(AccessResolution.NoFreeFullText, id = "W1000000001"),
            paperWith(AccessResolution.Paywalled, id = "W1000000002"),
            paperWith(AccessResolution.Unknown, id = "W1000000003"),
            paperWith(AccessResolution.Checking, id = "W1000000004"),
            paperWith(AccessResolution.Error, id = "W1000000005"),
        )
        setContentWith(silentPapers)

        composeRule.onNodeWithText("Download PDF").assertDoesNotExist()
        composeRule.onNodeWithText("Read online").assertDoesNotExist()
    }

    private fun setContentWith(papers: List<Paper>) {
        composeRule.setContent {
            PaperReaderTheme {
                LibraryScreen(
                    state = LibraryUiState(content = LibraryContent.Loaded(papers)),
                    onOpenPaper = {},
                    onToggleSave = {},
                    onStartDownload = {},
                    onReadOnline = {},
                )
            }
        }
    }

    private fun paperWith(access: AccessResolution, id: String = "W2741809807"): Paper {
        return Paper(id = PaperId(id), title = "Test paper $id", access = access)
    }
}
