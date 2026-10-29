package com.example.paperReader.ui.paperdetail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.Topic
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How the detail screen's topic pills share a line. The topics are shaped like the live backend's —
 * three per paper, several of them long — because the demo catalog's single short topic per paper
 * never exposed the squeeze.
 */
@RunWith(RobolectricTestRunner::class)
// Phone width, and tall enough that every section is laid out without scrolling.
@Config(sdk = [34], qualifiers = "w360dp-h2000dp")
// Real text measurement: the legacy graphics mode gives a label almost no width, which would make
// every pill fit on one line whatever the layout does.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaperDetailTopicsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortTopicsAfterALongOneKeepTheirLabelsOnOneLine() {
        setContentWith(LONG_TOPIC, "Education", "Neural Networks")

        assertEquals(1, composeRule.onNodeWithText("Education").textLayout().lineCount)
        assertEquals(1, composeRule.onNodeWithText("Neural Networks").textLayout().lineCount)
    }

    @Test
    fun aPillThatDoesNotFitMovesBelowThePreviousOne() {
        setContentWith(LONG_TOPIC, "Education")

        val first = composeRule.onNodeWithText(LONG_TOPIC).getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithText("Education").getUnclippedBoundsInRoot()
        assertTrue("the second pill starts a new line", second.top >= first.bottom)
        assertEquals("the new line starts at the row's start", first.left, second.left)
    }

    @Test
    fun aTopicWiderThanTheRowWrapsOnlyBetweenWords() {
        setContentWith(OVERSIZED_TOPIC, "Education")

        val row = composeRule.onNodeWithTag(PAPER_DETAIL_TOPICS_TEST_TAG).getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText(OVERSIZED_TOPIC)
        assertTrue("stays inside the row", label.getUnclippedBoundsInRoot().right <= row.right)

        val layout = label.textLayout()
        assertTrue("needs more than one line at this width", layout.lineCount > 1)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue(
                "line $line ends inside a word: \"${OVERSIZED_TOPIC.substring(0, end)}\"",
                OVERSIZED_TOPIC[end - 1] == ' ' || OVERSIZED_TOPIC[end] == ' ',
            )
        }
    }

    private fun setContentWith(vararg topics: String) {
        composeRule.setContent {
            PaperReaderTheme {
                PaperDetailScreen(
                    state = PaperDetailUiState(
                        content = PaperDetailContent.Loaded(
                            paper = Paper(
                                id = PaperId("W2741809807"),
                                title = "Harnessing Generative AI for Automated Feedback in Higher Education",
                                topics = topics.map(::Topic),
                                access = AccessResolution.NoFreeFullText,
                            ),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onToggleSave = {},
                    onReadPdf = {},
                    onReadOnline = {},
                )
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        /** A real topic name from the live backend, wide enough to leave a short pill no room. */
        const val LONG_TOPIC = "Impact of AI and Big Data on Business and Society"

        /** Wider than a phone-width row on its own, so it has to wrap however it is laid out. */
        const val OVERSIZED_TOPIC =
            "Impact of Artificial Intelligence and Big Data Analytics on Business, Society and Public Policy"
    }
}
