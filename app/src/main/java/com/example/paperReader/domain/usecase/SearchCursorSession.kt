package com.example.paperReader.domain.usecase

import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult

/**
 * What one load action (a first page, or an append) concluded.
 *
 * Deliberately not "did this response contain items": a page with no visible papers but a live
 * cursor is not the end of the query, and the register (AND-004) recorded exactly that confusion —
 * a server-side filtered page stranding valid later results behind a terminal-looking empty state.
 */
sealed interface PageLoadOutcome {
    /** Papers were fetched and persisted. [hasMore] reports whether a cursor remains. */
    data class Loaded(val hasMore: Boolean) : PageLoadOutcome

    /** The sequence ended with no next cursor: this query really has nothing more. */
    data object Terminal : PageLoadOutcome

    /**
     * The automatic empty-page bound was reached while a new cursor was still being offered. The
     * cursor is retained, so the user can continue — this is "nothing yet", never "no matches".
     */
    data object EmptyPageBoundReached : PageLoadOutcome

    /** Nothing was persisted. Already loaded items and the retry cursor are untouched. */
    data class Failed(val error: RepositoryError) : PageLoadOutcome
}

/**
 * One query's cursor traversal, scoped to that query and nothing else.
 *
 * It lives below Compose on purpose: recomposition is a poor loop controller, and letting the UI
 * re-issue `loadNextPage` whenever it saw an empty list is how a duplicate-request or infinite-spin
 * bug gets written. Here the loop is bounded, every cursor visited is remembered, and a repeated or
 * non-advancing cursor stops the traversal instead of driving it round again.
 *
 * A new query, filter, or sort gets a new session; the old one is simply discarded along with the
 * coroutine collecting it.
 */
class SearchCursorSession(
    private val query: PaperQuery,
    private val searchPapers: SearchPapersUseCase,
    private val maxAutomaticEmptyPages: Int = MAX_AUTOMATIC_EMPTY_PAGES,
) {
    private val visitedCursors = mutableSetOf<String>()

    /** The cursor a continuation would use, or null when the sequence is terminal. */
    var nextCursor: String? = null
        private set

    val hasMore: Boolean get() = nextCursor != null

    suspend fun loadFirstPage(): PageLoadOutcome {
        visitedCursors.clear()
        nextCursor = null
        return traverse(startCursor = null)
    }

    /** Continues from the retained cursor. Returns [PageLoadOutcome.Terminal] when there is none. */
    suspend fun loadNextPage(): PageLoadOutcome {
        val cursor = nextCursor ?: return PageLoadOutcome.Terminal
        return traverse(startCursor = cursor)
    }

    /**
     * Consumes pages until one has visible papers, the sequence ends, the empty-page bound is
     * reached, or something fails. Every page in between is persisted by the repository, so the
     * action publishes one logical result rather than a flicker of intermediate empty states.
     */
    private suspend fun traverse(startCursor: String?): PageLoadOutcome {
        var cursor = startCursor
        var consecutiveEmptyPages = 0

        while (true) {
            // Checked *before* the request: a cursor we have already followed in this session can
            // only take us round the same loop again.
            if (cursor != null && !visitedCursors.add(cursor)) {
                return cursorContractFailure()
            }

            val result = searchPapers(PaperSearchRequest(query, cursor))
            if (result is RepositoryResult.Failure) {
                return PageLoadOutcome.Failed(result.error)
            }

            val page = (result as RepositoryResult.Success).value
            nextCursor = page.nextCursor

            if (page.items.isNotEmpty()) {
                return PageLoadOutcome.Loaded(hasMore = page.nextCursor != null)
            }

            val continuation = page.nextCursor ?: return PageLoadOutcome.Terminal
            if (continuation == cursor) {
                return cursorContractFailure()
            }

            consecutiveEmptyPages += 1
            if (consecutiveEmptyPages >= maxAutomaticEmptyPages) {
                return PageLoadOutcome.EmptyPageBoundReached
            }
            cursor = continuation
        }
    }

    private fun cursorContractFailure(): PageLoadOutcome.Failed {
        return PageLoadOutcome.Failed(
            RepositoryError.Contract(RepositoryError.ContractFailure.CURSOR_DID_NOT_ADVANCE),
        )
    }

    companion object {
        /**
         * How many consecutive empty pages one action will consume before handing control back.
         *
         * A bound, not a fix: aggregating upstream pages so a client page is useful is the server's
         * job (TECHNICAL_DEBT.md API-005). This only stops the client from either giving up too
         * early or hammering the backend.
         */
        const val MAX_AUTOMATIC_EMPTY_PAGES = 3
    }
}
