package com.example.paperReader.contract

import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.RemoteJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource
import retrofit2.Retrofit

/**
 * A real local HTTP server wired to the *production* [PaperReaderApi] interface, [RemoteJson]
 * converter, and [HttpPaperRemoteDataSource].
 *
 * The register (SHR-001) found drift precisely at the seams an in-process API double skips: query
 * encoding, HTTP status handling, and serialization. Nothing here substitutes production behavior —
 * only the base URL differs.
 */
class ContractServer : ExternalResource() {
    private lateinit var server: MockWebServer

    lateinit var api: PaperReaderApi
        private set

    lateinit var source: HttpPaperRemoteDataSource
        private set

    override fun before() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .callTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(RemoteJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PaperReaderApi::class.java)
        source = HttpPaperRemoteDataSource(api)
    }

    override fun after() {
        runCatching { server.shutdown() }
    }

    /** Replies with a pinned fixture from the contract bundle. */
    fun enqueueFixture(relativePath: String, status: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(ContractBundle.readText(relativePath)),
        )
    }

    /** Replies with a status whose body must never reach presentation state. */
    fun enqueueError(status: Int, bodyFixture: String) {
        enqueueFixture(bodyFixture, status)
    }

    fun enqueueRaw(status: Int, body: String, contentType: String = "application/json") {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", contentType).setBody(body))
    }

    fun takeRequest(): RecordedRequest = server.takeRequest()

    /** Makes the next call fail at the transport layer, as an unreachable backend would. */
    fun shutdownBeforeRequest() {
        server.shutdown()
    }

    private companion object {
        const val READ_TIMEOUT_SECONDS = 10L
    }
}

/** Asserts a recorded request satisfies a pinned [ExpectedRequest]. */
fun RecordedRequest.assertMatches(expectation: ExpectedRequest) {
    val url = requireNotNull(requestUrl) { "Recorded request has no URL" }
    check(method == expectation.method) {
        "${expectation.name}: expected ${expectation.method} but was $method"
    }
    check(url.encodedPath == expectation.path) {
        "${expectation.name}: expected path ${expectation.path} but was ${url.encodedPath}"
    }
    expectation.query.forEach { (name, value) ->
        check(url.queryParameter(name) == value) {
            "${expectation.name}: expected $name=$value but was ${url.queryParameter(name)} " +
                "(${expectation.description})"
        }
    }
    expectation.absentQueryParameters.forEach { name ->
        check(name !in url.queryParameterNames) {
            "${expectation.name}: $name must not be expressed at all, but was " +
                "${url.queryParameter(name)} (${expectation.description})"
        }
    }
}
