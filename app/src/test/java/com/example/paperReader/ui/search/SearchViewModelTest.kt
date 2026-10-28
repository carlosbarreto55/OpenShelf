package com.example.paperReader.ui.search

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.PublicationYear
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.FakeLibraryRepository
import com.example.paperReader.domain.usecase.SearchPapersUseCase
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.domain.usecase.testPaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSearchPaperRepository
    private lateinit var libraryRepository: FakeLibraryRepository
    private lateinit var downloadsRepository: FakeDownloadsRepository
    private lateinit var demoDisclosurePreferences: FakeDemoDisclosurePreferences
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSearchPaperRepository()
        libraryRepository = FakeLibraryRepository()
        downloadsRepository = FakeDownloadsRepository()
        demoDisclosurePreferences = FakeDemoDisclosurePreferences()
        viewModel = searchViewModel()
        // WhileSubscribed(5000) means uiState only runs its upstream while collected; keep one
        // collector alive for the whole test so state updates as soon as they happen.
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
    }

    /** A ViewModel over the shared fakes. Called again in the persistence tests to stand in for a
     * process or ViewModel recreation while the stored acknowledgement survives. */
    private fun searchViewModel(
        mode: BackendMode = BackendMode.FAKE,
    ) = SearchViewModel(
        repository,
        SearchPapersUseCase(repository),
        ToggleSavedPaperUseCase(libraryRepository),
        downloadsRepository,
        demoDisclosurePreferences,
        BackendConfiguration(
            mode = mode,
            baseUrl = if (mode == BackendMode.HTTP) "https://paperreader.invalid/" else null,
        ),
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blankQueryIssuesNoSearch() = runTest(dispatcher) {
        viewModel.onQueryTextChanged("   ")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.searchCalls.isEmpty())
        assertEquals(SearchContent.Initial, viewModel.uiState.value.content)
    }

    @Test
    fun rapidTypingOnlyRequestsTheSettledQuery() = runTest(dispatcher) {
        viewModel.onQueryTextChanged("a")
        dispatcher.scheduler.advanceTimeBy(100)
        dispatcher.scheduler.runCurrent()
        viewModel.onQueryTextChanged("ab")
        dispatcher.scheduler.advanceTimeBy(100)
        dispatcher.scheduler.runCurrent()
        viewModel.onQueryTextChanged("abc")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.searchCalls.size)
        assertEquals("abc", repository.searchCalls.single().query.text.value)
    }

    @Test
    fun filterChangeRequestsImmediatelyWithoutWaitingForDebounce() = runTest(dispatcher) {
        viewModel.onQueryTextChanged("abc")
        dispatcher.scheduler.advanceUntilIdle()
        repository.searchCalls.clear()

        viewModel.onFiltersChanged(PaperFilters(pdfAvailableOnly = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.searchCalls.size)
        val request = repository.searchCalls.single()
        assertEquals("abc", request.query.text.value)
        assertTrue(request.query.filters.pdfAvailableOnly)
    }

    @Test
    fun olderResponseIsSuppressedOnceTheQueryHasMovedOn() = runTest(dispatcher) {
        val staleMarker = testPaper("W_STALE")
        val freshMarker = testPaper("W_FRESH")
        repository.searchHandler = { request ->
            if (request.query.text.value == "first") {
                delay(500)
                RepositoryResult.Success(CursorPage(items = listOf(staleMarker)))
            } else {
                RepositoryResult.Success(CursorPage(items = listOf(freshMarker)))
            }
        }

        viewModel.onQueryTextChanged("first")
        dispatcher.scheduler.advanceTimeBy(301) // let "first" settle and start its 500ms fetch
        dispatcher.scheduler.runCurrent()
        viewModel.onQueryTextChanged("second")
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is SearchContent.Loaded)
        val papers = (content as SearchContent.Loaded).papers
        assertEquals(listOf(freshMarker.id), papers.map { it.id })
    }

    @Test
    fun retryReissuesSearchForTheActiveQuery() = runTest(dispatcher) {
        repository.searchHandler = { RepositoryResult.Failure(RepositoryError.Offline) }
        viewModel.onQueryTextChanged("abc")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.content is SearchContent.Error)

        repository.searchCalls.clear()
        viewModel.retry()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.searchCalls.size)
        assertEquals("abc", repository.searchCalls.single().query.text.value)
    }

    @Test
    fun successfulSearchWithNoMatchesIsEmpty() = runTest(dispatcher) {
        repository.searchHandler = { RepositoryResult.Success(CursorPage(items = emptyList())) }
        viewModel.onQueryTextChanged("nothing")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SearchContent.Empty("nothing"), viewModel.uiState.value.content)
    }

    @Test
    fun loadNextPageAppendsUniquePapersAndClearsHasMore() = runTest(dispatcher) {
        val firstPagePaper = testPaper("W1")
        val secondPagePaper = testPaper("W2")
        repository.searchHandler = { request ->
            if (request.cursor == null) {
                RepositoryResult.Success(CursorPage(items = listOf(firstPagePaper), nextCursor = "next"))
            } else {
                RepositoryResult.Success(CursorPage(items = listOf(secondPagePaper), nextCursor = null))
            }
        }

        viewModel.onQueryTextChanged("paged")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContent.Loaded
        assertEquals(listOf(firstPagePaper.id, secondPagePaper.id), content.papers.map { it.id })
        assertEquals(false, content.hasMore)
        assertEquals(AppendState.IDLE, content.appendState)
    }

    @Test
    fun loadNextPageFailureKeepsExistingResultsAndReportsAppendError() = runTest(dispatcher) {
        val firstPagePaper = testPaper("W1")
        var callCount = 0
        repository.searchHandler = { request ->
            callCount++
            if (request.cursor == null) {
                RepositoryResult.Success(CursorPage(items = listOf(firstPagePaper), nextCursor = "next"))
            } else {
                RepositoryResult.Failure(RepositoryError.Offline)
            }
        }

        viewModel.onQueryTextChanged("paged")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContent.Loaded
        assertEquals(listOf(firstPagePaper.id), content.papers.map { it.id })
        assertEquals(AppendState.ERROR, content.appendState)
    }

    @Test
    fun toggleSavedCallsLibraryRepositoryBasedOnCurrentState() = runTest(dispatcher) {
        val unsavedPaper = testPaper("W1")
        viewModel.toggleSaved(unsavedPaper)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(unsavedPaper), libraryRepository.savedCalls)
        assertTrue(libraryRepository.unsavedCalls.isEmpty())
    }

    @Test
    fun startDownloadDelegatesToDownloadsRepository() = runTest(dispatcher) {
        val paper = testPaper("W1")
        viewModel.startDownload(paper)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(paper), downloadsRepository.startDownloadCalls)
    }

    @Test
    fun anEmptyFirstPageWithACursorNeverShowsATerminalEmptyState() = runTest(dispatcher) {
        val laterPaper = testPaper("W1")
        val observedStates = mutableListOf<SearchContent>()
        viewModel.uiState.onEach { observedStates += it.content }.launchIn(CoroutineScope(dispatcher))
        repository.searchHandler = { request ->
            if (request.cursor == null) {
                RepositoryResult.Success(CursorPage(items = emptyList(), nextCursor = "c1"))
            } else {
                RepositoryResult.Success(CursorPage(items = listOf(laterPaper), nextCursor = null))
            }
        }

        viewModel.onQueryTextChanged("filtered")
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContent.Loaded
        assertEquals(listOf(laterPaper.id), content.papers.map { it.id })
        assertTrue(
            "A terminal empty state must never flash before the later page arrives",
            observedStates.none { it is SearchContent.Empty },
        )
    }

    @Test
    fun exhaustingTheEmptyPageBoundOffersContinuationRatherThanClaimingNoMatches() = runTest(dispatcher) {
        var index = 0
        repository.searchHandler = {
            index += 1
            RepositoryResult.Success(CursorPage(items = emptyList(), nextCursor = "c$index"))
        }

        viewModel.onQueryTextChanged("stubborn")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SearchContent.MorePagesPending("stubborn"), viewModel.uiState.value.content)
    }

    @Test
    fun aRepeatedCursorSurfacesAsARecoverableFirstPageProblemWithoutLooping() = runTest(dispatcher) {
        repository.searchHandler = {
            RepositoryResult.Success(CursorPage(items = emptyList(), nextCursor = "loop"))
        }

        viewModel.onQueryTextChanged("looping")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is SearchContent.Error)
        assertEquals("It must stop, not spin", 2, repository.searchCalls.size)
    }

    @Test
    fun anOverlappingAppendAddsOnlyTheNewPapersAndKeepsTheOriginalOrder() = runTest(dispatcher) {
        val first = testPaper("W1")
        val second = testPaper("W2")
        val third = testPaper("W3")
        repository.searchHandler = { request ->
            if (request.cursor == null) {
                RepositoryResult.Success(CursorPage(items = listOf(first, second), nextCursor = "next"))
            } else {
                RepositoryResult.Success(CursorPage(items = listOf(second, third, first), nextCursor = null))
            }
        }

        viewModel.onQueryTextChanged("overlapping")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContent.Loaded
        assertEquals(listOf(first.id, second.id, third.id), content.papers.map { it.id })
    }

    @Test
    fun aSuppliedTotalReachesTheUiAndAnAbsentOneStaysAbsent() = runTest(dispatcher) {
        repository.searchHandler = {
            RepositoryResult.Success(CursorPage(items = listOf(testPaper("W1")), totalItems = 125))
        }
        viewModel.onQueryTextChanged("counted")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(125, (viewModel.uiState.value.content as SearchContent.Loaded).totalItems)

        repository.searchHandler = {
            RepositoryResult.Success(CursorPage(items = listOf(testPaper("W2")), totalItems = null))
        }
        viewModel.onQueryTextChanged("uncounted")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull((viewModel.uiState.value.content as SearchContent.Loaded).totalItems)
    }

    @Test
    fun anAppendThatOmitsTheTotalPreservesTheOneTheFirstPageSupplied() = runTest(dispatcher) {
        repository.searchHandler = { request ->
            if (request.cursor == null) {
                RepositoryResult.Success(
                    CursorPage(items = listOf(testPaper("W1")), nextCursor = "next", totalItems = 125),
                )
            } else {
                RepositoryResult.Success(CursorPage(items = listOf(testPaper("W2")), totalItems = null))
            }
        }

        viewModel.onQueryTextChanged("counted")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContent.Loaded
        assertEquals(2, content.papers.size)
        assertEquals("The total describes matches, not what has been loaded", 125, content.totalItems)
    }

    @Test
    fun twoPagesWithNoSuppliedTotalStillReportNoTotal() = runTest(dispatcher) {
        // The defect this guards: substituting what has been loaded for what matched, so that
        // "3 results" silently becomes "6 results" as the user pages.
        repository.searchHandler = { request ->
            if (request.cursor == null) {
                RepositoryResult.Success(CursorPage(items = listOf(testPaper("W1")), nextCursor = "next"))
            } else {
                RepositoryResult.Success(CursorPage(items = listOf(testPaper("W2"))))
            }
        }

        viewModel.onQueryTextChanged("uncounted")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull((viewModel.uiState.value.content as SearchContent.Loaded).totalItems)

        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContent.Loaded
        assertEquals(2, content.papers.size)
        assertNull(
            "An absent total stayed absent only until a page count could stand in for it",
            content.totalItems,
        )
    }

    @Test
    fun aFailedDownloadConfirmationIsSurfacedRatherThanDiscarded() = runTest(dispatcher) {
        downloadsRepository.startResult = RepositoryResult.Failure(RepositoryError.NoDownloadableContent)

        viewModel.startDownload(testPaper("W1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            RepositoryError.NoDownloadableContent,
            viewModel.uiState.value.downloadFeedback?.error,
        )

        viewModel.dismissDownloadFeedback()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.downloadFeedback)
    }

    @Test
    fun applyingAYearOrTypeStartsANewFirstPageCarryingThatFilter() = runTest(dispatcher) {
        respondWithOnePaper()
        viewModel.onQueryTextChanged("filters")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFiltersChanged(PaperFilters(publicationYear = 2024, paperType = PaperType.BOOK_CHAPTER))
        dispatcher.scheduler.advanceUntilIdle()

        val latest = repository.searchCalls.last()
        assertNull("A filter change starts a new first page", latest.cursor)
        assertEquals(2024, latest.query.filters.publicationYear)
        assertEquals(PaperType.BOOK_CHAPTER, latest.query.filters.paperType)
    }

    @Test
    fun clearingOneFilterLeavesTheOthersActive() = runTest(dispatcher) {
        respondWithOnePaper()
        viewModel.onQueryTextChanged("filters")
        viewModel.onFiltersChanged(
            PaperFilters(openAccessOnly = true, publicationYear = 2024, paperType = PaperType.REVIEW),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFiltersChanged(viewModel.uiState.value.filters.copy(publicationYear = null))
        dispatcher.scheduler.advanceUntilIdle()

        val filters = repository.searchCalls.last().query.filters
        assertNull(filters.publicationYear)
        assertEquals(PaperType.REVIEW, filters.paperType)
        assertEquals(true, filters.openAccessOnly)
    }

    @Test
    fun clearAllRemovesEveryFilterIncludingYearAndType() = runTest(dispatcher) {
        respondWithOnePaper()
        viewModel.onQueryTextChanged("filters")
        viewModel.onFiltersChanged(
            PaperFilters(openAccessOnly = true, pdfAvailableOnly = true, publicationYear = 2024, paperType = PaperType.REVIEW),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.clearFilters()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaperFilters(), repository.searchCalls.last().query.filters)
        assertEquals(PaperFilters(), viewModel.uiState.value.filters)
    }

    @Test
    fun yearAndTypeSurviveSortingAndPagination() = runTest(dispatcher) {
        repository.searchHandler = { request ->
            RepositoryResult.Success(
                CursorPage(items = listOf(testPaper("W" + repository.searchCalls.size)), nextCursor = "next"),
            )
        }
        viewModel.onQueryTextChanged("filters")
        viewModel.onFiltersChanged(PaperFilters(publicationYear = 2024, paperType = PaperType.DATASET))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSortOrderChanged(SortOrder.CITATIONS)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        val latest = repository.searchCalls.last()
        assertEquals("next", latest.cursor)
        assertEquals(SortOrder.CITATIONS, latest.query.sortOrder)
        assertEquals(2024, latest.query.filters.publicationYear)
        assertEquals(PaperType.DATASET, latest.query.filters.paperType)
    }

    @Test
    fun anOutOfRangeYearIsNeverRequested() = runTest(dispatcher) {
        // The control refuses to apply an invalid year, so no such filter can reach the ViewModel;
        // this pins the rule the control validates against.
        assertTrue(PublicationYear.validate("1200") is PublicationYear.Validation.OutOfRange)
        assertTrue(PublicationYear.validate("20xx") is PublicationYear.Validation.NotANumber)
        assertTrue(PublicationYear.validate("") is PublicationYear.Validation.Empty)
        assertEquals(
            PublicationYear.Validation.Valid(2024),
            PublicationYear.validate(" 2024 "),
        )
    }

    private fun respondWithOnePaper() {
        repository.searchHandler = { RepositoryResult.Success(CursorPage(items = listOf(testPaper("W1")))) }
    }
}
