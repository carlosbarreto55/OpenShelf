package com.example.paperReader.domain.usecase

import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.ui.search.FakeSearchPaperRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-004: cursor progress is independent of whether the current page happens to contain visible
 * papers, the traversal is bounded, and a repeated cursor stops it rather than driving it round.
 */
class SearchCursorSessionTest {

    private val query = PaperQuery(text = SearchQuery("ai"))
    private val repository = FakeSearchPaperRepository()
    private val searchPapers = SearchPapersUseCase(repository)

    @Test
    fun anEmptyFirstPageWithACursorContinuesUntilPapersAppear() = runTest {
        respondWith(
            page(items = emptyList(), nextCursor = "c1"),
            page(items = listOf(paper("W1")), nextCursor = null),
        )

        val outcome = session().loadFirstPage()

        assertEquals(PageLoadOutcome.Loaded(hasMore = false), outcome)
        assertEquals(listOf(null, "c1"), repository.searchCalls.map { it.cursor })
    }

    @Test
    fun anEmptyAppendPageWithACursorContinuesUntilPapersAppear() = runTest {
        respondWith(
            page(items = listOf(paper("W1")), nextCursor = "c1"),
            page(items = emptyList(), nextCursor = "c2"),
            page(items = listOf(paper("W2")), nextCursor = null),
        )
        val session = session()
        session.loadFirstPage()

        val outcome = session.loadNextPage()

        assertEquals(PageLoadOutcome.Loaded(hasMore = false), outcome)
        assertEquals(listOf(null, "c1", "c2"), repository.searchCalls.map { it.cursor })
    }

    @Test
    fun aSequenceThatEndsWithNoCursorIsTerminal() = runTest {
        respondWith(page(items = emptyList(), nextCursor = null))

        val session = session()
        val outcome = session.loadFirstPage()

        assertEquals(PageLoadOutcome.Terminal, outcome)
        assertNull(session.nextCursor)
        assertTrue(!session.hasMore)
    }

    @Test
    fun theBoundStopsAutomaticTraversalWhileRetainingTheCursor() = runTest {
        // Every response is empty but keeps offering a new cursor; without a bound this loops.
        var index = 0
        repository.searchHandler = {
            index += 1
            RepositoryResult.Success(page(items = emptyList(), nextCursor = "c$index"))
        }
        val session = session()

        val outcome = session.loadFirstPage()

        assertEquals(PageLoadOutcome.EmptyPageBoundReached, outcome)
        assertEquals(SearchCursorSession.MAX_AUTOMATIC_EMPTY_PAGES, repository.searchCalls.size)
        assertTrue("The cursor must be retained so the user can continue", session.hasMore)
    }

    @Test
    fun continuingAfterTheBoundPicksUpFromTheRetainedCursor() = runTest {
        var index = 0
        repository.searchHandler = {
            index += 1
            if (index <= SearchCursorSession.MAX_AUTOMATIC_EMPTY_PAGES) {
                RepositoryResult.Success(page(items = emptyList(), nextCursor = "c$index"))
            } else {
                RepositoryResult.Success(page(items = listOf(paper("W1")), nextCursor = null))
            }
        }
        val session = session()
        session.loadFirstPage()
        val retained = session.nextCursor

        val outcome = session.loadNextPage()

        assertEquals(PageLoadOutcome.Loaded(hasMore = false), outcome)
        assertEquals(retained, repository.searchCalls.last().cursor)
    }

    @Test
    fun aRepeatedCursorStopsTheTraversalWithAPaginationContractFailure() = runTest {
        // The server keeps handing back a cursor this session has already followed.
        repository.searchHandler = { RepositoryResult.Success(page(items = emptyList(), nextCursor = "loop")) }
        val session = session()

        val outcome = session.loadFirstPage()

        assertEquals(
            PageLoadOutcome.Failed(
                RepositoryError.Contract(RepositoryError.ContractFailure.CURSOR_DID_NOT_ADVANCE),
            ),
            outcome,
        )
        assertEquals("It must stop after one repeat, not loop", 2, repository.searchCalls.size)
    }

    @Test
    fun aCursorThatPointsAtItselfIsRejectedBeforeAnotherRequest() = runTest {
        respondWith(
            page(items = listOf(paper("W1")), nextCursor = "c1"),
            page(items = emptyList(), nextCursor = "c1"),
        )
        val session = session()
        session.loadFirstPage()

        val outcome = session.loadNextPage()

        assertEquals(
            PageLoadOutcome.Failed(
                RepositoryError.Contract(RepositoryError.ContractFailure.CURSOR_DID_NOT_ADVANCE),
            ),
            outcome,
        )
    }

    @Test
    fun aFailedContinuationKeepsTheRetryCursorSoTheUserCanTryAgain() = runTest {
        respondWith(page(items = listOf(paper("W1")), nextCursor = "c1"))
        val session = session()
        session.loadFirstPage()
        repository.searchHandler = { RepositoryResult.Failure(RepositoryError.Offline) }

        val outcome = session.loadNextPage()

        assertEquals(PageLoadOutcome.Failed(RepositoryError.Offline), outcome)
        assertEquals("c1", session.nextCursor)
    }

    @Test
    fun continuingWithNoCursorIsTerminalWithoutIssuingARequest() = runTest {
        respondWith(page(items = listOf(paper("W1")), nextCursor = null))
        val session = session()
        session.loadFirstPage()
        val callsAfterFirstPage = repository.searchCalls.size

        val outcome = session.loadNextPage()

        assertEquals(PageLoadOutcome.Terminal, outcome)
        assertEquals(callsAfterFirstPage, repository.searchCalls.size)
    }

    @Test
    fun anOverlapOnlyPageStillCountsAsLoadedBecauseTheResponseHadItems() = runTest {
        respondWith(
            page(items = listOf(paper("W1")), nextCursor = "c1"),
            page(items = listOf(paper("W1")), nextCursor = "c2"),
        )
        val session = session()
        session.loadFirstPage()

        val outcome = session.loadNextPage()

        // Deduplication is the cache's job; the traversal only decides whether to keep going.
        assertEquals(PageLoadOutcome.Loaded(hasMore = true), outcome)
    }

    private fun session() = SearchCursorSession(query, searchPapers)

    private fun respondWith(vararg pages: CursorPage<Paper>) {
        val queue = ArrayDeque(pages.toList())
        repository.searchHandler = { _: PaperSearchRequest ->
            RepositoryResult.Success(queue.removeFirstOrNull() ?: page(emptyList(), null))
        }
    }

    private fun page(items: List<Paper>, nextCursor: String?, totalItems: Int? = null) =
        CursorPage(items = items, nextCursor = nextCursor, totalItems = totalItems)

    private fun paper(id: String) = testPaper(id)
}
