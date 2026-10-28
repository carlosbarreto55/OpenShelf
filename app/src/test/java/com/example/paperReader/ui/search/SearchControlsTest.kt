package com.example.paperReader.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.R
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The touch area Compose actually dispatches to, which for a chip is deliberately larger than
 * the chip's own 32dp box. */
private fun SemanticsNodeInteraction.assertTouchHeightIsAtLeast(minimum: Dp) {
    val node = fetchSemanticsNode()
    val heightDp = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
    if (heightDp < minimum) {
        throw AssertionError("touch height $heightDp is below the $minimum floor")
    }
}

/**
 * The control region as a user meets it once the list is long: still there, still showing what is
 * applied, still operable — and not so tall that the first result has to be scrolled to.
 */
@RunWith(RobolectricTestRunner::class)
// A real phone viewport, not the tall one the state tests use: everything here is about what fits
// on screen and what happens when the list scrolls past it.
@Config(sdk = [34], qualifiers = "w412dp-h844dp")
class SearchControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val papers = (1..30).map { testPaper("W$it") }

    @Test
    fun theControlsSurviveScrollingPastTheFirstPage() {
        setContent(loadedState())

        composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).performScrollToIndex(papers.size - 1)

        // The query the visible results belong to, the chips that narrowed them, and the order
        // they are in: all reachable without scrolling back to the top.
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search_field_label))
            .assertTextContains("ai harness")
        assertEveryFilterControlIsOperable()
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.search_sort_label, context.getString(R.string.search_sort_newest)),
            )
            .assertIsDisplayed()
            .assert(hasClickAction())
    }

    @Test
    fun theControlsCarryTheSameSemanticsCollapsedAsExpanded() {
        setContent(loadedState())

        val atTop = controlSemantics()
        composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).performScrollToIndex(papers.size - 1)
        val scrolled = controlSemantics()

        // Same controls, same names, same order, same selected states — a screen-reader user is
        // not handed a different control set depending on the scroll position.
        assertTrue(
            "control semantics changed on scroll: $atTop -> $scrolled",
            atTop == scrolled,
        )
    }

    @Test
    fun changingAFilterFromTheScrolledControlsReturnsTheListToItsFirstItem() {
        var filters by mutableStateOf(PaperFilters())
        composeRule.setContent {
            PaperReaderTheme {
                SearchScreen(
                    state = loadedState().copy(filters = filters),
                    onQueryTextChanged = {},
                    onFiltersChanged = { filters = it },
                    onClearFilters = { filters = PaperFilters() },
                    onSortOrderChanged = {},
                    onRetry = {},
                    onLoadNextPage = {},
                    onToggleSave = {},
                    onOpenPaper = {},
                    onStartDownload = {},
                    onReadOnline = {},
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).performScrollToIndex(papers.size - 1)
        composeRule.onNodeWithText(papers.first().title).assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.filter_open_access)).performClick()
        composeRule.waitForIdle()

        // The old scroll position belonged to results that no longer apply, so the list is back at
        // its first item rather than stranded in the middle of a set that just changed.
        composeRule.onNodeWithText(papers.first().title).assertIsDisplayed()
    }

    @Test
    fun everyControlStaysAtOrAboveTheTouchTargetFloorInBothStates() {
        setContent(loadedState())

        assertTouchTargets()
        composeRule.onNodeWithTag(SEARCH_RESULT_LIST_TEST_TAG).performScrollToIndex(papers.size - 1)
        assertTouchTargets()
    }

    @Test
    fun theFirstResultCardIsFullyVisibleInTheFirstViewport() {
        setContent(loadedState())

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val card = composeRule
            .onAllNodesWithTag(SEARCH_RESULT_CARD_TEST_TAG)
            .onFirst()
            .getUnclippedBoundsInRoot()

        // Recorded for the change notes: at 412x844dp, default font scale, the first card's top
        // edge and its bottom edge both land inside the first content viewport.
        println(
            "first-result offset: card top=${card.top}, card bottom=${card.bottom}, viewport=${root.height}",
        )
        assertTrue("card top ${card.top} is off-screen", card.top < root.height)
        assertTrue(
            "card bottom ${card.bottom} exceeds the ${root.height} viewport",
            card.bottom <= root.height,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h844dp", fontScale = 2.0f)
    fun theControlsReflowAtTheLargestSupportedFontScaleWithoutClipping() {
        setContent(loadedState())

        // Nothing is dropped and nothing is squeezed below its floor: the region grows and the
        // results move down, rather than the layout breaking.
        assertEveryFilterControlIsOperable()
        assertTouchTargets()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search_field_label))
            .assertIsDisplayed()
    }

    private fun assertEveryFilterControlIsOperable() {
        val viewport = composeRule.onRoot().getUnclippedBoundsInRoot()
        listOf(
            R.string.filter_all,
            R.string.filter_open_access,
            R.string.filter_pdf_available,
            R.string.search_filters_label,
            R.string.search_sort_newest,
        ).forEach { labelRes ->
            val label = context.getString(labelRes)
            val node = composeRule.onNodeWithText(label).assertIsDisplayed().assert(hasClickAction())
            // Inside the viewport horizontally too: a control the user must discover a sideways
            // scroll to reach is not reachable in any useful sense.
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(
                "$label extends past the ${viewport.width} viewport (right edge ${bounds.right})",
                bounds.right <= viewport.width,
            )
        }
    }

    private fun assertTouchTargets() {
        val floor = 48.dp
        listOf(
            R.string.filter_all,
            R.string.filter_open_access,
            R.string.filter_pdf_available,
            R.string.search_filters_label,
        ).forEach { labelRes ->
            composeRule.onNodeWithText(context.getString(labelRes)).assertTouchHeightIsAtLeast(floor)
        }
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search_field_label))
            .assertHeightIsAtLeast(floor)
    }

    /** The control region reduced to what a screen reader would traverse: which controls exist, in
     * which order, under which names, and which of them are selected. */
    private fun controlSemantics(): String = buildList {
        add("query=" + context.getString(R.string.search_field_label))
        listOf(
            R.string.filter_all,
            R.string.filter_open_access,
            R.string.filter_pdf_available,
            R.string.search_filters_label,
        ).forEach { labelRes ->
            val label = context.getString(labelRes)
            val selected = composeRule.onNodeWithText(label).fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Selected)
            add("chip=$label selected=$selected")
        }
        add("sort=" + context.getString(R.string.search_sort_newest))
    }.joinToString("|")

    private fun loadedState() = SearchUiState(
        queryText = "ai harness",
        sortOrder = SortOrder.NEWEST,
        content = SearchContent.Loaded(papers = papers),
    )

    private fun setContent(state: SearchUiState) {
        composeRule.setContent {
            PaperReaderTheme {
                SearchScreen(
                    state = state,
                    onQueryTextChanged = {},
                    onFiltersChanged = {},
                    onClearFilters = {},
                    onSortOrderChanged = {},
                    onRetry = {},
                    onLoadNextPage = {},
                    onToggleSave = {},
                    onOpenPaper = {},
                    onStartDownload = {},
                    onReadOnline = {},
                )
            }
        }
    }
}
