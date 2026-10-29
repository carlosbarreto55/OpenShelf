package com.example.paperReader.ui.search

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.R
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.components.SKELETON_CARD_COUNT
import com.example.paperReader.ui.components.SKELETON_CARD_TEST_TAG
import com.example.paperReader.ui.components.SKELETON_LIST_TEST_TAG
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The discovery screen's non-content states as a user meets them: loading that holds the list's
 * geometry, an empty result that names what is narrowing it, and a count that never claims more
 * than the data supports.
 */
@RunWith(RobolectricTestRunner::class)
// A tall viewport so the whole screen — header, chrome, and the content region beneath it — is
// laid out at once; these assertions are about what is on screen, not about scrolling to it.
@Config(sdk = [34], qualifiers = "w412dp-h2400dp")
class SearchScreenStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun loadingRendersCardShapedPlaceholders() {
        setContent(SearchUiState(queryText = "ai harness", content = SearchContent.Loading))

        composeRule.onNodeWithTag(SKELETON_LIST_TEST_TAG).assertIsDisplayed()
        // The unmerged tree, because the placeholders are deliberately merged into their parent's
        // single announcement — that merge is the subject of the next test.
        composeRule
            .onAllNodesWithTag(SKELETON_CARD_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(SKELETON_CARD_COUNT)
    }

    @Test
    fun theSkeletonAnnouncesItselfOnceRatherThanOncePerPlaceholder() {
        setContent(SearchUiState(queryText = "ai harness", content = SearchContent.Loading))

        // One live region for the whole block; the placeholders clear their own semantics, so a
        // screen reader hears this once.
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search_loading_message))
            .assertIsDisplayed()
    }

    @Test
    fun resultsReplaceTheSkeletonsWithoutMovingTheSurroundingChrome() {
        var state by mutableStateOf(
            SearchUiState(queryText = "ai harness", content = SearchContent.Loading),
        )
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
                    onReadPdf = {},
                    onReadOnline = {},
                )
            }
        }

        // The chip row sits directly above the content region, so it is what would visibly jump if
        // the loading state replaced the list instead of standing in for it.
        val chromeWhileLoading = composeRule
            .onNodeWithText(context.getString(R.string.filter_open_access))
            .getUnclippedBoundsInRoot()

        state = state.copy(
            content = SearchContent.Loaded(papers = listOf(testPaper("W1"), testPaper("W2"))),
        )
        composeRule.waitForIdle()

        val chromeWithResults = composeRule
            .onNodeWithText(context.getString(R.string.filter_open_access))
            .getUnclippedBoundsInRoot()

        assertEquals(chromeWhileLoading.top, chromeWithResults.top)
        assertEquals(chromeWhileLoading.left, chromeWithResults.left)
        composeRule
            .onAllNodesWithTag(SKELETON_CARD_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun anEmptyResultWithActiveFiltersNamesEachOneAndRemovesJustThatOne() {
        var filters = PaperFilters(openAccessOnly = true, publicationYear = 2021)
        setContent(
            SearchUiState(
                queryText = "ai harness",
                filters = filters,
                content = SearchContent.Empty("ai harness"),
            ),
            onFiltersChanged = { filters = it },
        )

        // Asserted by the removal affordance rather than the bare label: the chip row above also
        // carries an "Open access" chip, and that one toggles the filter rather than removing it.
        val openAccess = context.getString(R.string.filter_open_access)
        val year = context.getString(R.string.filter_year_active, 2021)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search_remove_filter, openAccess))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search_remove_filter, year))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_remove_filter, year))
            .performClick()

        // Only the year is gone; the other filter is untouched, so the user is not thrown back to
        // an unfiltered search they did not ask for.
        assertEquals(PaperFilters(openAccessOnly = true, publicationYear = null), filters)
    }

    @Test
    fun anEmptyResultOffersClearAllAlongsideTheIndividualRemovals() {
        var cleared = 0
        setContent(
            SearchUiState(
                queryText = "ai harness",
                filters = PaperFilters(pdfAvailableOnly = true, paperType = PaperType.REVIEW),
                content = SearchContent.Empty("ai harness"),
            ),
            onClearFilters = { cleared++ },
        )

        composeRule.onNodeWithText(context.getString(R.string.search_clear_all_filters)).performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun anEmptyResultWithNoFiltersSaysNothingAboutFilters() {
        setContent(
            SearchUiState(
                queryText = "ai harness",
                filters = PaperFilters(),
                content = SearchContent.Empty("ai harness"),
            ),
        )

        composeRule
            .onNodeWithText(context.getString(R.string.search_empty_description_unfiltered))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.search_empty_description_filtered))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(context.getString(R.string.search_clear_all_filters))
            .assertDoesNotExist()
    }

    @Test
    fun withoutASuppliedTotalTheCountStatesOnlyWhatIsShown() {
        val papers = listOf(testPaper("W1"), testPaper("W2"))
        setContent(
            SearchUiState(
                queryText = "ai harness",
                content = SearchContent.Loaded(papers = papers, totalItems = null),
            ),
        )

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(
                    R.plurals.search_result_count,
                    papers.size,
                    papers.size,
                    "ai harness",
                ),
            )
            .assertIsDisplayed()
        // "of" only ever appears in the shown-of-total form, which has no total to render here.
        composeRule.onNodeWithText(" of ", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSuppliedTotalIsStatedAlongsideWhatIsShown() {
        val papers = listOf(testPaper("W1"), testPaper("W2"))
        setContent(
            SearchUiState(
                queryText = "ai harness",
                content = SearchContent.Loaded(papers = papers, totalItems = 125),
            ),
        )

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(
                    R.plurals.search_result_count_of_total,
                    125,
                    papers.size,
                    125,
                    "ai harness",
                ),
            )
            .assertIsDisplayed()
    }

    @Test
    fun cachedResultsAfterAFailedRefreshDoNotClaimAMatchedTotal() {
        // The stored total was true when the query last reached the backend. While the refresh is
        // failing, the papers it counts are exactly the ones the client cannot get to, so stating
        // it reads as "keep scrolling, there are 123 more" directly above a notice saying these
        // results came off the device.
        val papers = listOf(testPaper("W1"), testPaper("W2"))
        setContent(
            SearchUiState(
                queryText = "ai harness",
                content = SearchContent.Loaded(papers = papers, isStale = true, totalItems = 125),
            ),
        )

        composeRule.onNodeWithText("125", substring = true).assertDoesNotExist()
        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(
                    R.plurals.search_result_count_cached,
                    papers.size,
                    papers.size,
                    "ai harness",
                ),
            )
            .assertIsDisplayed()
    }

    @Test
    fun aFreshlyLoadedPageStillStatesItsMatchedTotal() {
        // The stale case must not cost the honest case its total.
        val papers = listOf(testPaper("W1"), testPaper("W2"))
        setContent(
            SearchUiState(
                queryText = "ai harness",
                content = SearchContent.Loaded(papers = papers, isStale = false, totalItems = 125),
            ),
        )

        composeRule.onNodeWithText("125", substring = true).assertIsDisplayed()
    }

    // The states added here are about the *search*, not about any paper, so none of them may
    // introduce a badge, a lock, or an action — the fail-closed access rule holds by the simple
    // fact that no access affordance exists for these states to render.
    @Test
    fun loadingSaysNothingAboutAPapersAccess() {
        setContent(SearchUiState(queryText = "ai harness", content = SearchContent.Loading))

        assertNoAccessAffordance()
    }

    @Test
    fun theEmptyStateSaysNothingAboutAPapersAccess() {
        setContent(SearchUiState(queryText = "ai harness", content = SearchContent.Empty("ai harness")))

        assertNoAccessAffordance()
    }

    @Test
    fun theErrorStateSaysNothingAboutAPapersAccess() {
        setContent(
            SearchUiState(queryText = "ai harness", content = SearchContent.Error(RepositoryError.Offline)),
        )

        assertNoAccessAffordance()
    }

    private fun assertNoAccessAffordance() {
        composeRule.onNodeWithText("Read PDF").assertDoesNotExist()
        composeRule.onNodeWithText("Read online").assertDoesNotExist()
    }

    private fun setContent(
        state: SearchUiState,
        onFiltersChanged: (PaperFilters) -> Unit = {},
        onClearFilters: () -> Unit = {},
    ) {
        composeRule.setContent {
            PaperReaderTheme {
                SearchScreen(
                    state = state,
                    onQueryTextChanged = {},
                    onFiltersChanged = onFiltersChanged,
                    onClearFilters = onClearFilters,
                    onSortOrderChanged = {},
                    onRetry = {},
                    onLoadNextPage = {},
                    onToggleSave = {},
                    onOpenPaper = {},
                    onReadPdf = {},
                    onReadOnline = {},
                )
            }
        }
    }
}
