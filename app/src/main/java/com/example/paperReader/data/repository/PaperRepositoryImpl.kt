package com.example.paperReader.data.repository

import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.LocalMappers.toDomain
import com.example.paperReader.data.local.LocalMappers.toEntity
import com.example.paperReader.data.local.LocalMappers.toSnapshotEntity
import com.example.paperReader.data.local.dao.AccessSnapshotDao
import com.example.paperReader.data.local.dao.PaperDao
import com.example.paperReader.data.local.dao.RecentSearchDao
import com.example.paperReader.data.local.dao.SearchCacheDao
import com.example.paperReader.data.local.entity.RecentSearchEntity
import com.example.paperReader.data.local.entity.SearchQueryEntity
import com.example.paperReader.data.local.toCacheKey
import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.data.remote.toDto
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PaperRepositoryImpl @Inject constructor(
    private val remote: PaperRemoteDataSource,
    private val paperDao: PaperDao,
    private val searchCacheDao: SearchCacheDao,
    private val accessSnapshotDao: AccessSnapshotDao,
    private val recentSearchDao: RecentSearchDao,
    private val clock: Clock,
) : PaperRepository {

    override fun observeSearch(query: PaperQuery): Flow<CursorPage<Paper>> {
        val key = query.toCacheKey()
        return combine(
            searchCacheDao.observeResults(key),
            searchCacheDao.observeQuery(key),
        ) { rows, queryEntity ->
            CursorPage(
                items = rows.map { it.toDomain(ACCESS_FRESHNESS_MILLIS, clock.nowEpochMillis()) },
                nextCursor = queryEntity?.nextCursor,
                totalItems = queryEntity?.totalCount,
            )
        }
    }

    override suspend fun search(request: PaperSearchRequest): RepositoryResult<CursorPage<Paper>> {
        return when (val remoteResult = remote.search(request.toDto())) {
            is RepositoryResult.Failure -> remoteResult
            is RepositoryResult.Success -> {
                when (val mapped = PaperMappers.mapSearchResponse(remoteResult.value)) {
                    is RepositoryResult.Failure -> mapped
                    is RepositoryResult.Success -> {
                        persistPage(request, mapped.value)
                        RepositoryResult.Success(mapped.value)
                    }
                }
            }
        }
    }

    override fun observePaper(paperId: PaperId): Flow<Paper?> {
        return paperDao.observeRow(paperId.value).map { row ->
            row?.toDomain(ACCESS_FRESHNESS_MILLIS, clock.nowEpochMillis())
        }
    }

    override suspend fun refreshPaper(paperId: PaperId): RepositoryResult<Paper> {
        return when (val remoteResult = remote.getPaper(paperId)) {
            is RepositoryResult.Failure -> remoteResult
            is RepositoryResult.Success -> {
                when (val mapped = PaperMappers.mapDetailResponse(remoteResult.value, paperId)) {
                    is RepositoryResult.Failure -> mapped
                    is RepositoryResult.Success -> {
                        val now = clock.nowEpochMillis()
                        paperDao.upsertAll(listOf(mapped.value.toEntity(now)))
                        accessSnapshotDao.upsert(mapped.value.access.toSnapshotEntity(paperId.value, now))
                        RepositoryResult.Success(mapped.value)
                    }
                }
            }
        }
    }

    override suspend fun confirmDownloadAvailability(paperId: PaperId): RepositoryResult<DownloadAvailability> {
        return when (val remoteResult = remote.getDownloadDescriptor(paperId)) {
            is RepositoryResult.Failure -> remoteResult
            is RepositoryResult.Success -> PaperMappers.mapDownloadAvailability(
                dto = remoteResult.value,
                expectedPaperId = paperId,
                nowEpochMillis = clock.nowEpochMillis(),
            )
        }
    }

    override fun observeRecentQueries(limit: Int): Flow<List<String>> {
        return recentSearchDao.observeRecent(limit).map { entries -> entries.map { it.queryText } }
    }

    override suspend fun recordRecentQuery(query: SearchQuery) {
        recentSearchDao.upsert(RecentSearchEntity(queryText = query.value, lastUsedAtEpochMillis = clock.nowEpochMillis()))
        recentSearchDao.trimToMostRecent(MAX_RECENT_QUERIES)
    }

    override suspend fun clearRecentQueries() {
        recentSearchDao.clearAll()
    }

    override suspend fun pruneStaleCache() {
        val threshold = clock.nowEpochMillis() - CACHE_MAX_AGE_MILLIS
        searchCacheDao.deleteStaleQueries(threshold)
        paperDao.deleteOrphanedPapers()
    }

    private suspend fun persistPage(request: PaperSearchRequest, page: CursorPage<Paper>) {
        val key = request.query.toCacheKey()
        val now = clock.nowEpochMillis()
        val isFirstPage = request.cursor == null

        val queryEntity = SearchQueryEntity(
            queryKey = key,
            queryText = request.query.text.value,
            sortOrder = request.query.sortOrder.wireValue,
            openAccessOnly = request.query.filters.openAccessOnly,
            pdfAvailableOnly = request.query.filters.pdfAvailableOnly,
            publicationYear = request.query.filters.publicationYear,
            workType = request.query.filters.paperType?.name,
            topic = request.query.filters.topic,
            nextCursor = page.nextCursor,
            totalCount = resolveTotal(key, page, isFirstPage),
            fetchedAtEpochMillis = now,
        )

        searchCacheDao.persistPage(
            query = queryEntity,
            papers = page.items.map { it.toEntity(now) },
            accessSnapshots = page.items.map { it.access.toSnapshotEntity(it.id.value, now) },
            orderedPaperIds = page.items.map { it.id.value },
            isFirstPage = isFirstPage,
        )
    }

    /**
     * A first page states the total, including stating that there isn't one — a fresh execution of
     * the query must not keep showing a count the server has stopped supplying. An append only
     * updates it when the response actually carried one, so paging never silently drops a total the
     * first page established. Nothing is ever derived from the cached row count (AND-007).
     */
    private suspend fun resolveTotal(queryKey: String, page: CursorPage<Paper>, isFirstPage: Boolean): Int? {
        if (isFirstPage) return page.totalItems
        return page.totalItems ?: searchCacheDao.getQuery(queryKey)?.totalCount
    }

    private companion object {
        const val CACHE_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MAX_RECENT_QUERIES = 20
    }
}
