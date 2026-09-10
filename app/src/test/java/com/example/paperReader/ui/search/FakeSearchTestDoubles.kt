package com.example.paperReader.ui.search

import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** In-memory [PaperRepository] test double: [search] both records every call it receives and
 * writes the configured result into the same query-keyed cache [observeSearch] reads from,
 * mirroring how the real Room-backed repository makes a fetch observable. */
class FakeSearchPaperRepository : PaperRepository {
    var searchHandler: suspend (PaperSearchRequest) -> RepositoryResult<CursorPage<Paper>> =
        { RepositoryResult.Success(CursorPage(emptyList())) }

    val searchCalls = mutableListOf<PaperSearchRequest>()
    val recordedQueries = mutableListOf<String>()

    private val cache = mutableMapOf<String, MutableStateFlow<CursorPage<Paper>>>()
    private val recentQueries = MutableStateFlow<List<String>>(emptyList())

    override fun observeSearch(query: PaperQuery): Flow<CursorPage<Paper>> {
        return cache.getOrPut(query.cacheKey()) { MutableStateFlow(CursorPage(emptyList())) }
    }

    override suspend fun search(request: PaperSearchRequest): RepositoryResult<CursorPage<Paper>> {
        searchCalls += request
        val result = searchHandler(request)
        if (result is RepositoryResult.Success) {
            val flow = cache.getOrPut(request.query.cacheKey()) { MutableStateFlow(CursorPage(emptyList())) }
            // Mirrors the real Room-backed cache: one entry per stable paper ID in first-seen
            // order, and a total that a first page states outright but an append only updates
            // when the response actually supplied one.
            flow.value = if (request.cursor == null) {
                result.value.copy(items = result.value.items.distinctBy { it.id })
            } else {
                val existingIds = flow.value.items.map { it.id }.toSet()
                CursorPage(
                    items = flow.value.items + result.value.items
                        .distinctBy { it.id }
                        .filterNot { it.id in existingIds },
                    nextCursor = result.value.nextCursor,
                    totalItems = result.value.totalItems ?: flow.value.totalItems,
                )
            }
        }
        return result
    }

    override fun observePaper(paperId: PaperId): Flow<Paper?> = flowOf(null)

    override suspend fun refreshPaper(paperId: PaperId): RepositoryResult<Paper> =
        RepositoryResult.Failure(RepositoryError.Unexpected)

    var confirmDownloadHandler: suspend (PaperId) -> RepositoryResult<DownloadAvailability> = {
        RepositoryResult.Failure(RepositoryError.Unexpected)
    }

    val confirmDownloadCalls = mutableListOf<PaperId>()

    override suspend fun confirmDownloadAvailability(paperId: PaperId): RepositoryResult<DownloadAvailability> {
        confirmDownloadCalls += paperId
        return confirmDownloadHandler(paperId)
    }

    override fun observeRecentQueries(limit: Int): Flow<List<String>> = recentQueries

    override suspend fun recordRecentQuery(query: SearchQuery) {
        recordedQueries += query.value
        recentQueries.value = listOf(query.value) + recentQueries.value.filterNot { it == query.value }
    }

    override suspend fun clearRecentQueries() {
        recentQueries.value = emptyList()
    }

    override suspend fun pruneStaleCache() = Unit

    private fun PaperQuery.cacheKey(): String = "${text.value}|$filters|$sortOrder"
}
