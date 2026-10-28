package com.example.paperReader.ui.navigation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paperReader.MainActivity
import com.example.paperReader.ui.search.SEARCH_RESULT_CARD_TEST_TAG
import com.example.paperReader.ui.search.SEARCH_RESULT_LIST_TEST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tapping the tab that paper detail was opened from returns to that tab's screen as it was left.
 * Demo-only: it searches the fixture catalog, where "AI" matches every paper — enough results to
 * scroll on a phone.
 */
@RunWith(AndroidJUnit4::class)
class TabReselectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchTabFromDetailReturnsToTheResultsAtTheirScrollPosition() {
        search("AI")
        val list = composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG)
        list.performScrollToIndex(lastLoadedIndex())
        composeRule.waitForIdle()
        val scrolledTo = list.scrollOffset()
        assertTrue("the results scrolled", scrolledTo > 0f)

        composeRule.onAllNodesWithTag(SEARCH_RESULT_CARD_TEST_TAG).onLast().performClick()
        composeRule.onNodeWithContentDescription(BACK).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SEARCH_TAB).assertIsSelected()

        composeRule.onNodeWithContentDescription(SEARCH_TAB).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(BACK).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(SEARCH_TAB).assertIsSelected()
        assertEquals(scrolledTo, composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).scrollOffset())
    }

    @Test
    fun libraryTabFromDetailReturnsToTheLibrary() {
        search("AI")
        val title = composeRule.onAllNodesWithTag(SEARCH_RESULT_CARD_TEST_TAG).onFirst()
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        composeRule.onAllNodesWithContentDescription(SAVE_PAPER).onFirst().performClick()
        try {
            composeRule.onNodeWithContentDescription(LIBRARY_TAB).performClick()
            composeRule.onNodeWithText(title, substring = true).performClick()
            composeRule.onNodeWithContentDescription(BACK).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(LIBRARY_TAB).assertIsSelected()

            composeRule.onNodeWithContentDescription(LIBRARY_TAB).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription(BACK).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(LIBRARY_TAB).assertIsSelected()
            composeRule.onNodeWithText(title, substring = true).assertIsDisplayed()
        } finally {
            // The library outlives this test — MainActivityTest expects it empty — so the paper is
            // un-saved the way a user would, from the Library tab.
            composeRule.onNodeWithContentDescription(LIBRARY_TAB).performClick()
            composeRule.onNodeWithContentDescription(REMOVE_FROM_LIBRARY).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun search(query: String) {
        composeRule.onNodeWithContentDescription(SEARCH_FIELD).performTextInput(query)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(SEARCH_RESULT_CARD_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The last item the list holds right now; scrolling to it may load another page, which is
     * fine — the point is only to be well away from the top. */
    private fun lastLoadedIndex(): Int {
        val info = composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.CollectionInfo]
        return info.rowCount - 1
    }

    private fun SemanticsNodeInteraction.scrollOffset(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private companion object {
        const val SEARCH_TAB = "Search"
        const val LIBRARY_TAB = "Library"
        const val SEARCH_FIELD = "Search papers"
        const val BACK = "Back"
        const val SAVE_PAPER = "Save paper"
        const val REMOVE_FROM_LIBRARY = "Remove from library"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
