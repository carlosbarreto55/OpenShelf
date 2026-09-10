package com.example.paperReader.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.paperReader.data.download.DownloadFileStorage
import com.example.paperReader.data.download.DownloadOutcome
import com.example.paperReader.data.download.OkHttpPdfFetcher
import com.example.paperReader.data.download.PdfDownloader
import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.RemoteJson
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit

/**
 * AND-001/AND-003 exit criteria, on a device: an integration-debug build must be able to reach a
 * PaperReader server over the documented loopback host and complete a *validated* PDF transfer.
 *
 * The server runs in-process on `localhost`, which is exactly the host the integrationDebug
 * network-security config allows; a build without `INTERNET` or without that allowance fails here.
 * It carries no OpenAlex credential and never contacts a real network.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LiveBackendConnectivityTest {

    private lateinit var server: MockWebServer
    private lateinit var source: HttpPaperRemoteDataSource
    private lateinit var downloadsDir: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(RemoteJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PaperReaderApi::class.java)
        source = HttpPaperRemoteDataSource(api)
        downloadsDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "live-backend-connectivity",
        ).apply { deleteRecursively() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        downloadsDir.deleteRecursively()
    }

    @Test
    fun searchReachesTheLocalBackendOverCleartextLoopback() = runBlocking {
        server.enqueue(jsonResponse(fixture("responses/search-normal.json")))

        val response = source.search(PaperSearchRequestDto(query = "attention"))

        val page = PaperMappers.mapSearchResponse((response as RepositoryResult.Success).value)
        val papers = (page as RepositoryResult.Success).value
        assertEquals(listOf("W2001", "W2002"), papers.items.map { it.id.value })
        assertEquals("/v1/papers/search", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun aConfirmedDescriptorCompletesAValidatedPdfTransfer() = runBlocking {
        server.enqueue(jsonResponse(fixture("responses/download-available.json")))
        val pdfBytes = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\ntrailer\n%%EOF\n".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setBody(Buffer().write(pdfBytes)),
        )

        val descriptorResponse = source.getDownloadDescriptor(PaperId("W2001"))
        val availability = PaperMappers.mapDownloadAvailability(
            dto = (descriptorResponse as RepositoryResult.Success).value,
            expectedPaperId = PaperId("W2001"),
            nowEpochMillis = 0L,
        )
        assertTrue(
            "The pinned fixture must resolve to an available descriptor, not the no-PDF outcome",
            (availability as RepositoryResult.Success).value is DownloadAvailability.Available,
        )
        server.takeRequest()

        // The descriptor fixture points at a public HTTPS host; the transfer under test is the one
        // this device can actually perform, so it is retargeted at the same local server.
        val downloader = PdfDownloader(
            fetcher = OkHttpPdfFetcher(OkHttpClient()),
            fileStorage = DownloadFileStorage(downloadsDir),
        )
        val outcome = downloader.download("W2001", server.url("/pdf/W2001.pdf").toString())

        assertTrue("A valid PDF body must complete: $outcome", outcome is DownloadOutcome.Completed)
        val completed = outcome as DownloadOutcome.Completed
        assertEquals(pdfBytes.size.toLong(), completed.totalBytes)
        assertTrue(completed.file.readBytes().decodeToString().startsWith("%PDF-"))
    }

    @Test
    fun aNonPdfBodyIsRejectedEvenWhenTheServerReports200() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setBody("<html><body>Sign in to continue</body></html>"),
        )

        val downloader = PdfDownloader(
            fetcher = OkHttpPdfFetcher(OkHttpClient()),
            fileStorage = DownloadFileStorage(downloadsDir),
        )
        val outcome = downloader.download("W2001", server.url("/pdf/W2001.pdf").toString())

        assertTrue(outcome is DownloadOutcome.Failed)
    }

    /** Reads the same pinned v1 contract bundle the JVM consumer tests use; the build wires
     * `src/test/resources` into the androidTest resources so there is only one copy. */
    private fun fixture(relativePath: String): String {
        val resource = "/contract/v1/$relativePath"
        return checkNotNull(javaClass.getResourceAsStream(resource)) { "Missing pinned fixture $resource" }
            .use { it.readBytes().decodeToString() }
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
