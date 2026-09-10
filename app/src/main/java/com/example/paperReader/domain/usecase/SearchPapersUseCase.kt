package com.example.paperReader.domain.usecase

import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject

/** Runs a search request and, for a first page, records the settled query text as a recent
 * search — the two steps a screen needs whenever a user's query/filter/sort selection settles. */
class SearchPapersUseCase @Inject constructor(
    private val repository: PaperRepository,
) {
    suspend operator fun invoke(request: PaperSearchRequest): RepositoryResult<CursorPage<Paper>> {
        if (request.cursor == null) {
            repository.recordRecentQuery(request.query.text)
        }
        return repository.search(request)
    }
}
