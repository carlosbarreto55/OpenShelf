package com.example.paperReader.ui.paperdetail

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
 * applied to the detail screen: a saved-but-inaccessible paper must render no access action here
 * either (offline-paper-library requirement: consistent access UI across search/detail/library). */
class PaperDetailScreenAccessSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmedDirectPdfShowsDownloadAction() {
        val paper = paperWith(
            AccessResolution.DirectPdf(ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W1.pdf")),
        )
        setContentWith(paper)

        composeRule.onNodeWithText("Download PDF").assertIsDisplayed()
    }

    @Test
    fun paywalledPaperExposesNoAccessAction() {
        setContentWith(paperWith(AccessResolution.Paywalled))

        composeRule.onNodeWithText("Download PDF").assertDoesNotExist()
        composeRule.onNodeWithText("Read online").assertDoesNotExist()
    }

    private fun setContentWith(paper: Paper) {
        composeRule.setContent {
            PaperReaderTheme {
                PaperDetailScreen(
                    state = PaperDetailUiState(content = PaperDetailContent.Loaded(paper = paper)),
                    onBack = {},
                    onRetry = {},
                    onToggleSave = {},
                    onDownloadPdf = {},
                    onReadOnline = {},
                )
            }
        }
    }

    private fun paperWith(access: AccessResolution, id: String = "W2741809807"): Paper {
        return Paper(id = PaperId(id), title = "Test paper $id", access = access)
    }
}
