package com.example.paperReader.data.download

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class PdfHttpResponse(
    val statusCode: Int,
    val contentLength: Long,
    val body: ByteArray,
)

/** Isolates the one real network call a download makes so [PdfDownloader]'s validation/state
 * logic can be unit tested against a fake, the same way [com.example.paperReader.data.remote.PaperReaderApi]
 * is faked for [com.example.paperReader.data.remote.HttpPaperRemoteDataSource]'s tests. */
fun interface PdfHttpFetcher {
    @Throws(IOException::class)
    suspend fun fetch(url: String): PdfHttpResponse
}

class OkHttpPdfFetcher @Inject constructor(
    private val client: OkHttpClient,
) : PdfHttpFetcher {
    override suspend fun fetch(url: String): PdfHttpResponse {
        return withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                PdfHttpResponse(
                    statusCode = response.code,
                    contentLength = response.body?.contentLength()?.takeIf { it >= 0 } ?: bytes.size.toLong(),
                    body = bytes,
                )
            }
        }
    }
}
