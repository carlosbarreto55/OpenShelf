package com.example.paperReader.contract

import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.toDto
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The consumer half of SHR-001: every fixture the **provider publishes** driven through this
 * client's production Retrofit interface, [com.example.paperReader.data.remote.RemoteJson]
 * converter, status policy, and mappers.
 *
 * The provider drives the same file through Spring in `CanonicalProviderContractTest`. Two
 * repositories, one `paperreader-v1.0.0` bundle, one set of digests — which is the only thing that
 * shows the two sides actually agree. A green consumer suite on its own never did.
 */
class CanonicalContractTest {

    @get:Rule
    val server = ContractServer()

    // --- the vendored bundle is the published one -----------------------------------------------

    @Test
    fun everyVendoredFileMatchesThePublishedDigest() {
        val published = CanonicalBundle.publishedChecksums
        assertTrue("SHA256SUMS must pin at least one file", published.isNotEmpty())

        published.forEach { (relativePath, expectedDigest) ->
            assertEquals(
                "Vendored $relativePath is not the file the provider published. Re-vendor the whole " +
                    "directory from the PaperReaderApi release; never hand-edit it.",
                expectedDigest,
                CanonicalBundle.sha256(CanonicalBundle.readBytes(relativePath)),
            )
        }
    }

    @Test
    fun theVendoredBundleIsTheVersionThisClientPins() {
        assertEquals(PINNED_VERSION, CanonicalBundle.version)
        assertEquals(PINNED_VERSION, ContractBundle.manifest.contractVersion)
        assertEquals(
            "The provider's fixture file must declare the same contract version as VERSION.",
            PINNED_VERSION,
            CanonicalBundle.fixturesContractVersion,
        )
    }

    @Test
    fun everyPublishedFixtureIsAccountedFor() {
        val consumed = CONSUMED_FIXTURES + DELIBERATELY_UNCONSUMED_FIXTURES
        assertEquals(
            "A fixture the provider publishes is neither consumed nor explicitly waived. Every " +
                "canonical case must be a decision, not an oversight.",
            CanonicalBundle.fixtures.map { it.name }.sorted(),
            consumed.sorted(),
        )
    }

    /**
     * `/v1/papers/{id}/access:refresh` still exists server-side, and the provider still publishes a
     * fixture for it. AND-011 removed the *client* operation because it had no production call site;
     * detail refresh and download confirmation cover every freshness need. Waiving it here is the
     * record of that decision, so a future reader does not mistake the gap for an omission.
     */
    @Test
    fun theAccessRefreshFixtureIsWaivedNotMissed() {
        assertEquals(listOf("access-refresh-success"), DELIBERATELY_UNCONSUMED_FIXTURES)
        assertEquals("POST", CanonicalBundle.fixture("access-refresh-success").method)
    }

    // --- responses through the production stack -------------------------------------------------

    @Test
    fun aCanonicalSearchPageNormalizesItsWorkTypeAndCarriesItsCursorAndTotal() = runBlocking {
        enqueue("search-success-and-type-normalization")

        val page = mappedSearch()

        assertEquals(listOf("W2741809807"), page.items.map { it.id.value })
        assertEquals(PaperType.BOOK_CHAPTER, page.items.single().type)
        assertEquals("cursor-after-page", page.nextCursor)
        assertEquals(1, page.totalItems)
        assertTrue(page.items.single().access is AccessResolution.DirectPdf)
    }

    @Test
    fun aCanonicalEmptySearchIsTerminalAndNotAnError() = runBlocking {
        enqueue("search-empty")

        val page = mappedSearch()

        assertTrue(page.items.isEmpty())
        assertNull("No cursor means the traversal is over", page.nextCursor)
        assertEquals(0, page.totalItems)
    }

    @Test
    fun aCanonicalNullTotalStaysAbsentAndKeepsItsCursor() = runBlocking {
        enqueue("search-nullable-total")

        val page = mappedSearch()

        assertTrue(page.items.isEmpty())
        assertEquals("continue-after-four", page.nextCursor)
        assertNull("A null total is an absent total, never a synthesized one", page.totalItems)
    }

    @Test
    fun aCanonicalPaperDetailMapsToItsRequestedIdentity() = runBlocking {
        enqueue("paper-details-success")

        val paperId = PaperId("W2741809807")
        val response = server.source.getPaper(paperId)
        val paper = PaperMappers.mapDetailResponse((response as RepositoryResult.Success).value, paperId)

        val value = (paper as RepositoryResult.Success).value
        assertEquals(paperId, value.id)
        assertEquals("An unmapped upstream type becomes OTHER, never a dropped paper", PaperType.OTHER, value.type)
        assertEquals(AccessResolution.Unknown, value.access)
    }

    @Test
    fun aCanonicalDescriptorIsAConfirmedAvailability() = runBlocking {
        enqueue("download-success")

        val availability = mappedDescriptor(PaperId("W2741809807"))

        val value = (availability as RepositoryResult.Success).value
        assertTrue(value is DownloadAvailability.Available)
        assertEquals("https://example.org/paper.pdf", (value as DownloadAvailability.Available).descriptor.url)
    }

    @Test
    fun aCanonicalNullDescriptorIsTheNoPdfOutcomeNotAFailure() = runBlocking {
        enqueue("download-existing-no-pdf")

        val availability = mappedDescriptor(PaperId("W2741809807"))

        assertEquals(
            "200 with a matching paper_id and a null descriptor is a normal answer",
            DownloadAvailability.Unavailable,
            (availability as RepositoryResult.Success).value,
        )
    }

    // --- the published error matrix ---------------------------------------------------------------

    @Test
    fun theCanonicalErrorMatrixMapsToDistinctDomainOutcomes() = runBlocking {
        val expected = mapOf(
            "invalid-input" to RepositoryError.InvalidRequest,
            "paper-not-found" to RepositoryError.NotFound,
            "upstream-unavailable" to RepositoryError.ServiceUnavailable,
            "outbound-capacity" to RepositoryError.ServiceUnavailable,
            "upstream-timeout" to RepositoryError.Timeout,
        )

        expected.forEach { (name, error) ->
            val fixture = CanonicalBundle.fixture(name)
            enqueue(name)

            val result = if (fixture.path.startsWith("/v1/papers/search")) {
                server.source.search(PaperSearchRequestDto(query = "anything"))
            } else {
                server.source.getPaper(PaperId("W2741809807"))
            }

            assertEquals(
                "${fixture.status} ${name} must map to $error",
                error,
                (result as RepositoryResult.Failure).error,
            )
        }
    }

    @Test
    fun noProblemDetailFromAnErrorStatusEverReachesTheUser() = runBlocking {
        listOf("invalid-input", "paper-not-found", "upstream-unavailable", "outbound-capacity", "upstream-timeout")
            .forEach { name ->
                enqueue(name)
                val result = server.source.getPaper(PaperId("W2741809807"))
                val message = (result as RepositoryResult.Failure).error.userMessage

                // Every canonical Problem Detail carries `detail` and `type`; neither may surface.
                assertTrue(
                    "$name leaked the server's own words into '$message'",
                    "paperreader.app/problems" !in message && "OpenAlex" !in message,
                )
            }
    }

    // --- requests the provider recorded -----------------------------------------------------------

    @Test
    fun theProductionRequestBuilderProducesTheCanonicalRequestCases() = runBlocking {
        val cases = listOf(
            "search-success-and-type-normalization" to PaperFilters(),
            "search-nullable-total" to PaperFilters(pdfAvailableOnly = true),
        )

        cases.forEach { (name, filters) ->
            val fixture = CanonicalBundle.fixture(name)
            enqueue(name)

            search(query = fixture.query.getValue("query"), filters = filters)

            val url = requireNotNull(server.takeRequest().requestUrl)
            assertEquals("$name: path", fixture.path, url.encodedPath)
            fixture.query.forEach { (parameter, value) ->
                assertEquals("$name: $parameter", value, url.queryParameter(parameter))
            }
        }
    }

    @Test
    fun theCanonicalWorkTypeEnumRoundTripsThroughTheProductionBuilder() = runBlocking {
        // `book_chapter` is the value the provider's SearchWorkType enum accepts and the value its
        // PublicWorkType enum returns; the client must both emit and parse exactly that spelling.
        enqueue("search-success-and-type-normalization")

        search(query = "medieval history", filters = PaperFilters(paperType = PaperType.BOOK_CHAPTER))

        assertEquals(
            "book_chapter",
            requireNotNull(server.takeRequest().requestUrl).queryParameter("work_type"),
        )
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun enqueue(fixtureName: String) {
        val fixture = CanonicalBundle.fixture(fixtureName)
        server.enqueueRaw(status = fixture.status, body = CanonicalBundle.responseBody(fixtureName))
    }

    private suspend fun search(query: String, filters: PaperFilters = PaperFilters()) {
        val request = PaperSearchRequest(query = PaperQuery(text = SearchQuery(query), filters = filters))
        server.source.search(request.toDto())
    }

    private suspend fun mappedSearch() =
        PaperMappers.mapSearchResponse(
            (server.source.search(PaperSearchRequestDto(query = "anything")) as RepositoryResult.Success).value,
        ).let { (it as RepositoryResult.Success).value }

    private suspend fun mappedDescriptor(paperId: PaperId) =
        when (val response = server.source.getDownloadDescriptor(paperId)) {
            is RepositoryResult.Failure -> response
            is RepositoryResult.Success -> PaperMappers.mapDownloadAvailability(
                dto = response.value,
                expectedPaperId = paperId,
                nowEpochMillis = 0L,
            )
        }

    private companion object {
        const val PINNED_VERSION = "paperreader-v1.0.0"

        val CONSUMED_FIXTURES = listOf(
            "search-success-and-type-normalization",
            "search-empty",
            "search-nullable-total",
            "invalid-input",
            "paper-details-success",
            "paper-not-found",
            "download-success",
            "download-existing-no-pdf",
            "upstream-unavailable",
            "outbound-capacity",
            "upstream-timeout",
        )

        /** See [theAccessRefreshFixtureIsWaivedNotMissed]. */
        val DELIBERATELY_UNCONSUMED_FIXTURES = listOf("access-refresh-success")
    }
}
