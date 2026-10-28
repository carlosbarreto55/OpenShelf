package com.example.paperReader.data.download

import com.example.paperReader.domain.model.DownloadFailureReason
import java.io.File
import java.io.IOException
import javax.inject.Inject

sealed interface DownloadOutcome {
    data class Completed(val file: File, val totalBytes: Long) : DownloadOutcome

    data class Failed(val message: String) : DownloadOutcome
}

/** The production download path: fetch, validate as an actual PDF (never trust a 200 status or a
 * declared content type alone), and only then persist the bytes. Used by [PdfDownloadWorker]. */
class PdfDownloader @Inject constructor(
    private val fetcher: PdfHttpFetcher,
    private val fileStorage: DownloadFileStorage,
) {
    suspend fun download(downloadId: String, url: String): DownloadOutcome {
        val response = try {
            fetcher.fetch(url)
        } catch (e: IOException) {
            return DownloadOutcome.Failed(e.message ?: "The download could not be completed.")
        }

        if (!PdfValidation.isValidPdf(response.statusCode, response.contentLength, response.body)) {
            return DownloadOutcome.Failed(DownloadFailureReason.INVALID_PDF.message)
        }

        val file = fileStorage.write(downloadId, extension = "pdf", bytes = response.body)
        return DownloadOutcome.Completed(file = file, totalBytes = response.body.size.toLong())
    }
}
