package com.example.paperReader.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.paperReader.BuildConfig
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendConfigurationResult
import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.RemoteJson
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.toDto
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URI
import java.net.Socket
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit

/**
 * The one test in this repository that opens a socket to a **real PaperReader server**.
 *
 * [LiveBackendConnectivityTest] proves the device *can* reach a cleartext loopback address and
 * validate a PDF; it does so against an in-process `MockWebServer`, which means it can never catch
 * the two sides disagreeing. This one drives the configured
 * `BuildConfig.PAPER_READER_BACKEND_BASE_URL` through the production Retrofit interface,
 * [RemoteJson] converter, status policy, and [PaperMappers].
 *
 * It **skips**, rather than fails, when no server is listening — a developer without a backend
 * running is not a broken build. Run it with:
 *
 * ```
 * ./gradlew :app:atdIntegrationDebugAndroidTest -PpaperReaderBackendBaseUrl=http://10.0.2.2:8080
 * ```
 *
 * Nothing here hardcodes a paper ID. Real OpenAlex results churn, so every identity assertion is
 * derived from a search performed in the same run.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class RealBackendSmokeTest {

    private lateinit var source: HttpPaperRemoteDataSource

    @Before
    fun setUp() {
        assumeTrue("No PaperReader server is listening at $baseUrl", serverIsReachable)
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(RemoteJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PaperReaderApi::class.java)
        source = HttpPaperRemoteDataSource(api)
    }

    @Test
    fun aRealSearchReturnsAMappablePageWithACursorAndATotal() = runBlocking {
        val page = search(BROAD_QUERY)

        assertTrue("A broad query must match something", page.items.isNotEmpty())
        assertNotNull("A first page of a large result set must offer a cursor", page.nextCursor)
        assertNotNull("The server reports an upstream count for a normal page", page.totalItems)
        page.items.forEach {
            assertTrue("Every mapped paper keeps a usable identity", it.id.value.startsWith("W"))
            assertTrue("Every mapped paper keeps a non-blank title", it.title.isNotBlank())
        }
    }

    @Test
    fun followingTheCursorAdvancesWithoutRepeatingResults() = runBlocking {
        val first = search(BROAD_QUERY)
        val cursor = requireNotNull(first.nextCursor)

        val second = search(BROAD_QUERY, cursor = cursor)

        assertTrue("The second page must carry results", second.items.isNotEmpty())
        val overlap = first.items.map { it.id }.intersect(second.items.map { it.id }.toSet())
        assertTrue("Cursor paging must not re-serve page one: $overlap", overlap.isEmpty())
    }

    @Test
    fun aPaperFromASearchIsRetrievableByItsOwnIdentity() = runBlocking {
        val expected = search(BROAD_QUERY).items.first()

        val response = source.getPaper(expected.id)
        val detail = PaperMappers.mapDetailResponse((response as RepositoryResult.Success).value, expected.id)

        assertEquals(expected.id, (detail as RepositoryResult.Success).value.id)
    }

    @Test
    fun aConfirmedDirectPdfYieldsAFreshHttpsDescriptor() = runBlocking {
        val paper = firstPaperWhere { it.access is AccessResolution.DirectPdf }
        assumeTrue("No DIRECT_PDF paper on this page of live results", paper != null)

        val availability = descriptorFor(requireNotNull(paper).id)

        val value = (availability as RepositoryResult.Success).value
        assertTrue("A DIRECT_PDF paper must confirm at the download endpoint", value is DownloadAvailability.Available)
        assertTrue(
            "The fail-closed rule allows only an HTTPS descriptor to authorize a transfer",
            (value as DownloadAvailability.Available).descriptor.url.startsWith("https://"),
        )
    }

    @Test
    fun aPaperWithNoFreePdfIsANormalAnswerNotAFailure() = runBlocking {
        val paper = firstPaperWhere { it.access !is AccessResolution.DirectPdf }
        assumeTrue("Every paper on this page had a direct PDF", paper != null)

        val availability = descriptorFor(requireNotNull(paper).id)

        assertEquals(
            "200 with a matching paper_id and a null descriptor is the no-PDF outcome (decision 3)",
            DownloadAvailability.Unavailable,
            (availability as RepositoryResult.Success).value,
        )
    }

    @Test
    fun anUnknownPaperIsAFirstClassNotFound() = runBlocking {
        val result = source.getPaper(PaperId(UNKNOWN_BUT_WELL_FORMED_ID))

        assertEquals(
            "An unknown paper must be 404 -> NotFound, never a malformed-body contract failure",
            RepositoryError.NotFound,
            (result as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun aPdfOnlySearchCompletesWithinTheClientsTimeout() = runBlocking {
        // The server bounds a PDF-only scan at eight seconds by default and this client uses
        // Retrofit's ten-second default read timeout, so the margin is thin by construction.
        val page = search(BROAD_QUERY, filters = PaperFilters(pdfAvailableOnly = true))

        assertTrue("A PDF-only scan must return the papers it inspected", page.items.isNotEmpty())
        assertTrue(
            "Every result of a pdf_available scan must actually be downloadable",
            page.items.all { it.access is AccessResolution.DirectPdf },
        )
    }

    // --- helpers ------------------------------------------------------------------------------

    private suspend fun search(
        query: String,
        filters: PaperFilters = PaperFilters(),
        cursor: String? = null,
    ): CursorPage<Paper> {
        val request = PaperSearchRequest(
            query = PaperQuery(text = SearchQuery(query), filters = filters),
            cursor = cursor,
        )
        val response = source.search(request.toDto())
        val mapped = PaperMappers.mapSearchResponse(
            (response as? RepositoryResult.Success)?.value
                ?: error("Live search failed: ${(response as RepositoryResult.Failure).error}"),
        )
        return (mapped as RepositoryResult.Success).value
    }

    private suspend fun firstPaperWhere(predicate: (Paper) -> Boolean): Paper? =
        search(BROAD_QUERY).items.firstOrNull(predicate)

    private suspend fun descriptorFor(paperId: PaperId) =
        when (val response = source.getDownloadDescriptor(paperId)) {
            is RepositoryResult.Failure -> response
            is RepositoryResult.Success -> PaperMappers.mapDownloadAvailability(
                dto = response.value,
                expectedPaperId = paperId,
                nowEpochMillis = System.currentTimeMillis(),
            )
        }

    companion object {
        private const val BROAD_QUERY = "machine learning"

        /** Syntactically valid (`^W[0-9]+$`) so it reaches the server's not-found path, not its 400. */
        private const val UNKNOWN_BUT_WELL_FORMED_ID = "W99999999999"

        private lateinit var baseUrl: String
        private var serverIsReachable = false

        @BeforeClass
        @JvmStatic
        fun probeConfiguredBackend() {
            val result = BackendConfiguration.resolve(
                rawMode = BuildConfig.PAPER_READER_REMOTE_MODE,
                rawBaseUrl = BuildConfig.PAPER_READER_BACKEND_BASE_URL,
                isDebugBuild = BuildConfig.DEBUG,
            )
            val configuration = (result as? BackendConfigurationResult.Valid)?.configuration ?: return
            baseUrl = configuration.requireBaseUrl()

            val uri = URI(baseUrl)
            val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
            serverIsReachable = runCatching {
                Socket(uri.host, port).use { true }
            }.getOrDefault(false)
        }
    }
}
