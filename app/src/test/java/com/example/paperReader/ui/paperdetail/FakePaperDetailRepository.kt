package com.example.paperReader.ui.paperdetail

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

/** In-memory [PaperRepository] test double for paper-detail tests: [refreshPaper] both records
 * calls and, on success, writes into the same [observePaper] state the real Room-backed
 * repository would update, so a ViewModel under test sees its own fetch reflected. */
class FakePaperDetailRepository(initialPaper: Paper? = null) : PaperRepository {
    var refreshHandler: suspend (PaperId) -> RepositoryResult<Paper> = {
        RepositoryResult.Failure(RepositoryError.Unexpected)
    }

    val refreshCalls = mutableListOf<PaperId>()
    private val papers = MutableStateFlow(initialPaper)

    /** What the cached paper becomes, as a later refresh or another screen would leave it. */
    fun setPaper(paper: Paper?) {
        papers.value = paper
    }

    override fun observeSearch(query: PaperQuery): Flow<CursorPage<Paper>> = flowOf(CursorPage(emptyList()))

    override suspend fun search(request: PaperSearchRequest): RepositoryResult<CursorPage<Paper>> =
        RepositoryResult.Failure(RepositoryError.Unexpected)

    override fun observePaper(paperId: PaperId): Flow<Paper?> = papers

    override suspend fun refreshPaper(paperId: PaperId): RepositoryResult<Paper> {
        refreshCalls += paperId
        val result = refreshHandler(paperId)
        if (result is RepositoryResult.Success) {
            papers.value = result.value
        }
        return result
    }

    var confirmDownloadHandler: suspend (PaperId) -> RepositoryResult<DownloadAvailability> = {
        RepositoryResult.Failure(RepositoryError.Unexpected)
    }

    val confirmDownloadCalls = mutableListOf<PaperId>()

    override suspend fun confirmDownloadAvailability(paperId: PaperId): RepositoryResult<DownloadAvailability> {
        confirmDownloadCalls += paperId
        return confirmDownloadHandler(paperId)
    }

    override fun observeRecentQueries(limit: Int): Flow<List<String>> = flowOf(emptyList())

    override suspend fun recordRecentQuery(query: SearchQuery) = Unit

    override suspend fun clearRecentQueries() = Unit

    override suspend fun pruneStaleCache() = Unit
}
