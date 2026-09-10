package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import kotlinx.coroutines.flow.Flow

interface PaperRepository {
    fun observeSearch(query: PaperQuery): Flow<CursorPage<Paper>>

    suspend fun search(request: PaperSearchRequest): RepositoryResult<CursorPage<Paper>>

    fun observePaper(paperId: PaperId): Flow<Paper?>

    suspend fun refreshPaper(paperId: PaperId): RepositoryResult<Paper>

    /**
     * Asks the backend what, if anything, can be downloaded for [paperId] *right now*.
     *
     * This is the transfer-time freshness authority (business decision 5): a descriptor cached in
     * a [Paper] or a download record may have changed, expired, or been revoked. A successful
     * result is either a validated, unexpired HTTPS descriptor or the normal "no PDF" answer;
     * everything else is a typed failure.
     */
    suspend fun confirmDownloadAvailability(paperId: PaperId): RepositoryResult<DownloadAvailability>

    /** Bounded, deduplicated history of submitted query text (most recent first), independent of
     * the cached result pages themselves. */
    fun observeRecentQueries(limit: Int = DEFAULT_RECENT_QUERY_LIMIT): Flow<List<String>>

    suspend fun recordRecentQuery(query: SearchQuery)

    /** Removes recent-query history only; saved papers and downloads are untouched. */
    suspend fun clearRecentQueries()

    /** Prunes expired cached search queries/results and any paper no longer referenced by a saved
     * item, a download, or another cached query. */
    suspend fun pruneStaleCache()

    companion object {
        const val DEFAULT_RECENT_QUERY_LIMIT = 10
    }
}
