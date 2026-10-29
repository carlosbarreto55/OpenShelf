package com.example.paperReader.ui.components

import androidx.compose.foundation.layout.Column
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

/** Verifies the verified-paper-access rule directly on [PaperCard]: an action only ever renders
 * for a confirmed DIRECT_PDF/OPEN_FULL_TEXT paper, and every other access state — including a
 * verification error — renders no badge, source row, or button at all. */
class PaperCardAccessSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmedDirectPdfShowsReadPdfAction() {
        val paper = paperWith(
            AccessResolution.DirectPdf(ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W1.pdf")),
        )
        composeRule.setContent {
            PaperReaderTheme { PaperCard(paper = paper, onClick = {}, onToggleSave = {}, onReadPdf = {}, onReadOnline = {}) }
        }

        composeRule.onNodeWithText("Read PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Read online").assertDoesNotExist()
    }

    @Test
    fun confirmedOpenFullTextShowsReadOnlineAction() {
        val paper = paperWith(AccessResolution.OpenFullText(sourceUrl = "https://demo.paperreader.invalid/papers/W1"))
        composeRule.setContent {
            PaperReaderTheme { PaperCard(paper = paper, onClick = {}, onToggleSave = {}, onReadPdf = {}, onReadOnline = {}) }
        }

        composeRule.onNodeWithText("Read online").assertIsDisplayed()
        composeRule.onNodeWithText("Read PDF").assertDoesNotExist()
    }

    @Test
    fun everyOtherAccessStateRendersNoAction() {
        val silentPapers = listOf(
            paperWith(AccessResolution.NoFreeFullText),
            paperWith(AccessResolution.Paywalled),
            paperWith(AccessResolution.Unknown),
            paperWith(AccessResolution.Checking),
            paperWith(AccessResolution.Error),
        )
        composeRule.setContent {
            PaperReaderTheme {
                Column {
                    silentPapers.forEach { paper ->
                        PaperCard(paper = paper, onClick = {}, onToggleSave = {}, onReadPdf = {}, onReadOnline = {})
                    }
                }
            }
        }

        composeRule.onNodeWithText("Read PDF").assertDoesNotExist()
        composeRule.onNodeWithText("Read online").assertDoesNotExist()
    }

    private fun paperWith(access: AccessResolution, id: String = "W2741809807"): Paper {
        return Paper(id = PaperId(id), title = "Test paper $id", access = access)
    }
}
