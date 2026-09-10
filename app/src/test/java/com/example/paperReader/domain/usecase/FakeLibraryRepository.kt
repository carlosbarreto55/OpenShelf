package com.example.paperReader.domain.usecase

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LibraryRepository] test double shared by every ViewModel test that saves/unsaves
 * papers: [save]/[unsave] mutate the same list [observeSavedPapers] reads from, mirroring the
 * reactive Room-backed repository closely enough to exercise save-state consistency. */
class FakeLibraryRepository : LibraryRepository {
    val savedCalls = mutableListOf<Paper>()
    val unsavedCalls = mutableListOf<PaperId>()

    private val saved = MutableStateFlow<List<Paper>>(emptyList())

    override fun observeSavedPapers(): Flow<List<Paper>> = saved

    override suspend fun save(paper: Paper): RepositoryResult<Unit> {
        savedCalls += paper
        saved.value = listOf(paper) + saved.value.filterNot { it.id == paper.id }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun unsave(paperId: PaperId): RepositoryResult<Unit> {
        unsavedCalls += paperId
        saved.value = saved.value.filterNot { it.id == paperId }
        return RepositoryResult.Success(Unit)
    }
}

fun testPaper(
    id: String,
    access: AccessResolution = AccessResolution.Unknown,
    savedState: SavedState = SavedState.NOT_SAVED,
): Paper {
    return Paper(id = PaperId(id), title = "Paper $id", access = access, savedState = savedState)
}
