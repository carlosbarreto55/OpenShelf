package com.example.paperReader.contract

import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.toDto
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the client puts on the wire and what it makes of what comes back, against the pinned v1
 * bundle and a real HTTP server.
 *
 * Every request assertion is a recorded expectation under `contract/v1/requests`, so changing what
 * the client sends means changing a reviewed, checksum-pinned file — not quietly editing a test.
 */
class PaperReaderApiContractTest {

    @get:Rule
    val server = ContractServer()

    // --- requests (2.5) -----------------------------------------------------------------------

    @Test
    fun anUnrestrictedSearchExpressesNoOptionalRestrictionAtAll() = runBlocking {
        val expectation = ContractBundle.requestExpectation("search-default")
        server.enqueueFixture("responses/search-normal.json")

        search(query = "machine learning")

        server.takeRequest().assertMatches(expectation)
    }

    @Test
    fun anEnabledOpenAccessRestrictionIsSentAsTrue() = runBlocking {
        val expectation = ContractBundle.requestExpectation("search-open-access-only")
        server.enqueueFixture("responses/search-normal.json")

        search(query = "machine learning", filters = PaperFilters(openAccessOnly = true))

        server.takeRequest().assertMatches(expectation)
    }

    @Test
    fun anEnabledPdfRestrictionIsIndependentOfOpenAccess() = runBlocking {
        val expectation = ContractBundle.requestExpectation("search-pdf-available-only")
        server.enqueueFixture("responses/search-normal.json")

        search(query = "machine learning", filters = PaperFilters(pdfAvailableOnly = true))

        server.takeRequest().assertMatches(expectation)
    }

    @Test
    fun aTopicTravelsAsFreeText() = runBlocking {
        val expectation = ContractBundle.requestExpectation("search-topic-free-text")
        server.enqueueFixture("responses/search-normal.json")

        search(query = "learning outcomes", filters = PaperFilters(topic = "education"))

        server.takeRequest().assertMatches(expectation)
    }

    @Test
    fun aWorkTypeIsSentAsItsCanonicalValue() = runBlocking {
        val expectation = ContractBundle.requestExpectation("search-work-type-book-chapter")
        server.enqueueFixture("responses/search-normal.json")

        search(query = "medieval history", filters = PaperFilters(paperType = PaperType.BOOK_CHAPTER))

        server.takeRequest().assertMatches(expectation)
    }

    @Test
    fun cursorSortAndYearTravelTogetherOnAnAppend() = runBlocking {
        val expectation = ContractBundle.requestExpectation("search-append-page")
        server.enqueueFixture("responses/search-normal.json")

        search(
            query = "climate modelling",
            filters = PaperFilters(publicationYear = 2023),
            sortOrder = SortOrder.NEWEST,
            cursor = "cursor-page-2",
        )

        server.takeRequest().assertMatches(expectation)
    }

    @Test
    fun detailAndDownloadAreAddressedByTheStablePaperId() = runBlocking {
        server.enqueueFixture("responses/detail-found.json")
        server.source.getPaper(PaperId("W2001"))
        server.takeRequest().assertMatches(ContractBundle.requestExpectation("paper-detail"))

        server.enqueueFixture("responses/download-available.json")
        server.source.getDownloadDescriptor(PaperId("W2001"))
        server.takeRequest().assertMatches(ContractBundle.requestExpectation("download-descriptor"))
    }

    // --- responses (2.6) ----------------------------------------------------------------------

    @Test
    fun aNormalSearchDecodesAndMapsThroughTheProductionSerializer() = runBlocking {
        server.enqueueFixture("responses/search-normal.json")

        val page = mappedSearch()

        assertEquals(listOf("W2001", "W2002"), page.items.map { it.id.value })
        assertEquals("cursor-page-2", page.nextCursor)
        assertEquals(125, page.totalItems)
        assertEquals("Ada Reyes", page.items.first().authors.first().name)
    }

    @Test
    fun anEmptyTerminalSearchIsASuccessfulResultNotAnError() = runBlocking {
        server.enqueueFixture("responses/search-empty-terminal.json")

        val page = mappedSearch()

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
        assertEquals(0, page.totalItems)
    }

    @Test
    fun anEmptyPageThatStillOffersACursorKeepsThatCursor() = runBlocking {
        server.enqueueFixture("responses/search-empty-page-with-cursor.json")

        val page = mappedSearch()

        assertTrue(page.items.isEmpty())
        assertEquals("cursor-page-3", page.nextCursor)
    }

    @Test
    fun anAbsentTotalStaysAbsent() = runBlocking {
        server.enqueueFixture("responses/search-total-absent.json")

        assertNull(mappedSearch().totalItems)
    }

    @Test
    fun unknownOptionalFieldsAreIgnoredWithoutChangingTheDomainModel() = runBlocking {
        server.enqueueFixture("responses/search-unknown-fields.json")

        val page = mappedSearch()

        assertEquals(listOf("W2004"), page.items.map { it.id.value })
        assertEquals("Additive fields are ignored safely", page.items.single().title)
    }

    @Test
    fun everySupportedLegacyAndUnknownWorkTypeResolves() = runBlocking {
        server.enqueueFixture("responses/search-work-types.json")

        val types = mappedSearch().items.associate { it.id.value to it.type }

        assertEquals(PaperType.BOOK_CHAPTER, types["W2101"])
        assertEquals(PaperType.BOOK_CHAPTER, types["W2102"])
        assertEquals(PaperType.BOOK_CHAPTER, types["W2103"])
        assertEquals(PaperType.DATASET, types["W2104"])
        assertEquals(PaperType.ARTICLE, types["W2105"])
        assertEquals(PaperType.OTHER, types["W2106"])
        assertEquals(PaperType.UNKNOWN, types["W2107"])
        assertEquals(PaperType.UNKNOWN, types["W2108"])
    }

    @Test
    fun aPageContainingARepeatedIdStillDecodesEveryItem() = runBlocking {
        server.enqueueFixture("responses/search-duplicate-ids.json")

        // Deduplication is the cache's job, not the mapper's; the mapper must not silently
        // discard data the cache is responsible for reconciling.
        assertEquals(listOf("W2001", "W2201", "W2001"), mappedSearch().items.map { it.id.value })
    }

    @Test
    fun aDetailResponseMapsToTheRequestedPaper() = runBlocking {
        server.enqueueFixture("responses/detail-found.json")

        val response = server.source.getPaper(PaperId("W2001"))
        val paper = PaperMappers.mapDetailResponse(
            (response as RepositoryResult.Success).value,
            PaperId("W2001"),
        )

        assertEquals("W2001", (paper as RepositoryResult.Success).value.id.value)
        assertEquals(412, paper.value.citationCount)
    }

    @Test
    fun anAvailableDescriptorAndAnAbsentOneAreBothSuccessfulOutcomes() = runBlocking {
        server.enqueueFixture("responses/download-available.json")
        val available = mappedDescriptor()
        assertTrue(available is RepositoryResult.Success)

        server.enqueueFixture("responses/download-unavailable.json")
        val unavailable = mappedDescriptor()
        assertTrue(unavailable is RepositoryResult.Success)
    }

    // --- helpers ------------------------------------------------------------------------------

    private suspend fun search(
        query: String,
        filters: PaperFilters = PaperFilters(),
        sortOrder: SortOrder = SortOrder.RELEVANCE,
        cursor: String? = null,
    ) {
        val request = PaperSearchRequest(
            query = PaperQuery(text = SearchQuery(query), filters = filters, sortOrder = sortOrder),
            cursor = cursor,
        )
        server.source.search(request.toDto())
    }

    private suspend fun mappedSearch(): com.example.paperReader.domain.model.CursorPage<com.example.paperReader.domain.model.Paper> {
        val response = server.source.search(PaperSearchRequestDto(query = "anything"))
        val mapped = PaperMappers.mapSearchResponse((response as RepositoryResult.Success).value)
        return (mapped as RepositoryResult.Success).value
    }

    private suspend fun mappedDescriptor(paperId: PaperId = PaperId("W2001")) =
        when (val response = server.source.getDownloadDescriptor(paperId)) {
            is RepositoryResult.Failure -> response
            is RepositoryResult.Success -> PaperMappers.mapDownloadAvailability(
                dto = response.value,
                expectedPaperId = paperId,
                nowEpochMillis = 0L,
            )
        }
}
