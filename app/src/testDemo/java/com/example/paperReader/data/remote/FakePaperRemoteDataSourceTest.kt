package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.dto.SearchFiltersDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePaperRemoteDataSourceTest {
    private val source = FakePaperRemoteDataSource().apply { latencyMillis = 0 }

    @Before
    fun resetScenario() {
        source.forcedFailure = null
        source.scenario = FakeRemoteScenario.NORMAL
    }

    @Test
    fun servesSmallInMemoryPlaceholderCatalog() = runBlocking {
        val result = source.search(PaperSearchRequestDto(query = "AI"))

        val papers = (result as RepositoryResult.Success).value.items
        assertTrue(papers.isNotEmpty())
        assertTrue(papers.size <= 3)
    }

    @Test
    fun servesDetailsAndADownloadDescriptorForAKnownPlaceholderPaper() = runBlocking {
        val paperId = PaperId("W2741809807")

        val detail = source.getPaper(paperId)
        val descriptor = source.getDownloadDescriptor(paperId)

        assertTrue(detail is RepositoryResult.Success)
        assertTrue(descriptor is RepositoryResult.Success)
    }

    @Test
    fun unknownPaperIdReportsNotFoundJustAsHttp404Would() = runBlocking {
        val result = source.getPaper(PaperId("W0000000000"))

        assertEquals(RepositoryError.NotFound, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun paginatesThroughTheFullCatalogWithoutDuplicates() = runBlocking {
        val seenIds = mutableSetOf<String>()
        var cursor: String? = null
        var pages = 0

        do {
            val page = (source.search(PaperSearchRequestDto(query = "AI", cursor = cursor)) as RepositoryResult.Success).value
            page.items.forEach { assertTrue(seenIds.add(it.id!!)) }
            cursor = page.nextCursor
            pages++
        } while (cursor != null && pages < 10)

        assertEquals(3, pages)
        assertEquals(7, seenIds.size)
    }

    @Test
    fun pdfAvailableFilterReturnsOnlyDirectPdfPapers() = runBlocking {
        val result = source.search(
            PaperSearchRequestDto(query = "AI", filters = SearchFiltersDto(pdfAvailableOnly = true)),
        )

        val papers = (result as RepositoryResult.Success).value.items
        assertTrue(papers.isNotEmpty())
        assertTrue(papers.all { it.access?.state == "DIRECT_PDF" })
    }

    @Test
    fun sortingByCitationsOrdersResultsDescending() = runBlocking {
        val result = source.search(PaperSearchRequestDto(query = "AI", sort = "citations"))

        val counts = (result as RepositoryResult.Success).value.items.map { it.citationCount ?: 0 }
        assertEquals(counts.sortedDescending(), counts)
    }

    @Test
    fun forcedFailureIsReturnedForEveryOperationUntilCleared() = runBlocking {
        source.forcedFailure = RepositoryError.Offline

        val search = source.search(PaperSearchRequestDto(query = "AI"))
        val detail = source.getPaper(PaperId("W2741809807"))

        assertEquals(RepositoryError.Offline, (search as RepositoryResult.Failure).error)
        assertEquals(RepositoryError.Offline, (detail as RepositoryResult.Failure).error)

        source.forcedFailure = null
        val recovered = source.search(PaperSearchRequestDto(query = "AI"))
        assertTrue(recovered is RepositoryResult.Success)
    }

    @Test
    fun notFoundScenarioReportsTheSameOutcomeForEveryOperation() = runBlocking {
        source.scenario = FakeRemoteScenario.NOT_FOUND

        assertEquals(
            RepositoryError.NotFound,
            (source.getPaper(PaperId("W2741809807")) as RepositoryResult.Failure).error,
        )
        assertEquals(
            RepositoryError.NotFound,
            (source.getDownloadDescriptor(PaperId("W2741809807")) as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun noPdfScenarioReturnsTheRequestedPaperWithANullDescriptor() = runBlocking {
        source.scenario = FakeRemoteScenario.NO_PDF

        val response = (source.getDownloadDescriptor(PaperId("W2741809807")) as RepositoryResult.Success).value

        assertEquals("W2741809807", response.paperId)
        assertNull(response.descriptor)
    }

    @Test
    fun emptyPageScenarioKeepsHandingBackANewCursor() = runBlocking {
        source.scenario = FakeRemoteScenario.EMPTY_PAGES_WITH_CURSOR

        val first = (source.search(PaperSearchRequestDto(query = "AI")) as RepositoryResult.Success).value
        val second = (
            source.search(PaperSearchRequestDto(query = "AI", cursor = first.nextCursor))
                as RepositoryResult.Success
            ).value

        assertTrue(first.items.isEmpty())
        assertNotNull(first.nextCursor)
        assertTrue(second.items.isEmpty())
        assertNotEquals(first.nextCursor, second.nextCursor)
    }

    @Test
    fun upstreamScenariosMatchTheirHttpStatusCounterparts() = runBlocking {
        source.scenario = FakeRemoteScenario.UPSTREAM_UNAVAILABLE
        assertEquals(
            RepositoryError.ServiceUnavailable,
            (source.search(PaperSearchRequestDto(query = "AI")) as RepositoryResult.Failure).error,
        )

        source.scenario = FakeRemoteScenario.UPSTREAM_TIMEOUT
        assertEquals(
            RepositoryError.Timeout,
            (source.search(PaperSearchRequestDto(query = "AI")) as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun mismatchedIdentityScenarioAnswersAboutADifferentPaper() = runBlocking {
        source.scenario = FakeRemoteScenario.MISMATCHED_IDENTITY

        val detail = (source.getPaper(PaperId("W2741809807")) as RepositoryResult.Success).value
        val descriptor = (source.getDownloadDescriptor(PaperId("W2741809807")) as RepositoryResult.Success).value

        assertNotEquals("W2741809807", detail.paper?.id)
        assertNotEquals("W2741809807", descriptor.paperId)
    }

    @Test
    fun malformedScenarioReturnsA200BodyThatCannotBeMapped() = runBlocking {
        source.scenario = FakeRemoteScenario.MALFORMED_DATA

        val page = (source.search(PaperSearchRequestDto(query = "AI")) as RepositoryResult.Success).value

        assertEquals(1, page.items.size)
        assertNull(page.items.single().id)
        assertNull(page.items.single().title)
    }

    @Test
    fun queryWithNoMatchesReturnsEmptyPage() = runBlocking {
        val result = source.search(PaperSearchRequestDto(query = "no such topic exists"))

        val page = (result as RepositoryResult.Success).value
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }
}
