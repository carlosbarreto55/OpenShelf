package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePaperRemoteDataSourceTest {
    private val source = FakePaperRemoteDataSource()

    @Test
    fun servesSmallInMemoryPlaceholderCatalog() = runBlocking {
        val result = source.search(PaperSearchRequestDto(query = "AI"))

        val papers = (result as RepositoryResult.Success).value.items
        assertTrue(papers.isNotEmpty())
        assertTrue(papers.size <= 3)
    }

    @Test
    fun servesDetailsAndAccessForKnownPlaceholderPaper() = runBlocking {
        val paperId = PaperId("W2741809807")

        val detail = source.getPaper(paperId)
        val access = source.refreshAccess(paperId)

        assertTrue(detail is RepositoryResult.Success)
        assertTrue(access is RepositoryResult.Success)
    }
}
