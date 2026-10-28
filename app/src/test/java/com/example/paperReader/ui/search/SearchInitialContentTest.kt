package com.example.paperReader.ui.search

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.R
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.FakeLibraryRepository
import com.example.paperReader.domain.usecase.SearchPapersUseCase
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.theme.PaperReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screen before any query has been submitted. Its job is to start a search, so every test here
 * ends in one actually being issued rather than in copy being present.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2400dp")
class SearchInitialContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun withNoHistoryTheScreenStillOffersSomethingToSearch() {
        var submitted: String? = null
        setContent(SearchUiState(recentQueries = emptyList()), onQuerySelected = { submitted = it })

        composeRule.onNodeWithText(context.getString(R.string.search_suggestions_label)).assertIsDisplayed()
        // No "Recent" heading standing over nothing.
        composeRule.onNodeWithText(context.getString(R.string.search_recent_label)).assertDoesNotExist()

        val firstSuggestion = SUGGESTED_TOPICS.first()
        composeRule.onNodeWithText(firstSuggestion).performClick()

        assertEquals(firstSuggestion, submitted)
    }

    @Test
    fun recentQueriesAreOfferedAlongsideTheSuggestions() {
        setContent(SearchUiState(recentQueries = listOf("robotics")))

        composeRule.onNodeWithText(context.getString(R.string.search_recent_label)).assertIsDisplayed()
        composeRule.onNodeWithText("robotics").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_suggestions_label)).assertIsDisplayed()
        composeRule.onNodeWithText(SUGGESTED_TOPICS.first()).assertIsDisplayed()
    }

    @Test
    fun theSuggestionsMakeNoClaimTheClientCannotSupport() {
        // No counts, no ranking, no "recommended": the client has no source for any of that.
        assertTrue(SUGGESTED_TOPICS.none { it.any(Char::isDigit) })
        assertEquals(SUGGESTED_TOPICS.distinct(), SUGGESTED_TOPICS)
    }

    @Test
    fun activatingASuggestionIssuesTheSameSearchTypingItWouldAndIsRemembered() = runTest(dispatcher) {
        val repository = FakeSearchPaperRepository()
        repository.searchHandler = { RepositoryResult.Success(CursorPage(listOf(testPaper("W1")))) }
        val viewModel = SearchViewModel(
            repository,
            SearchPapersUseCase(repository),
            ToggleSavedPaperUseCase(FakeLibraryRepository()),
            FakeDownloadsRepository(),
            FakeDemoDisclosurePreferences(),
            BackendConfiguration(mode = BackendMode.FAKE, baseUrl = null),
        )
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))

        val topic = SUGGESTED_TOPICS.first()
        // The screen hands a suggestion to exactly the handler typed input uses.
        viewModel.onQueryTextChanged(topic)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(topic), repository.searchCalls.map { it.query.text.value })
        // Same path means the same side effects: it lands in recent searches like anything else.
        assertEquals(listOf(topic), repository.recordedQueries)
        assertTrue(viewModel.uiState.value.content is SearchContent.Loaded)
    }

    private fun setContent(
        state: SearchUiState,
        onQuerySelected: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            PaperReaderTheme {
                SearchScreen(
                    state = state,
                    onQueryTextChanged = onQuerySelected,
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
