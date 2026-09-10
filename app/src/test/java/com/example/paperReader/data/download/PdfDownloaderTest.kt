package com.example.paperReader.data.download

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfDownloaderTest {
    private lateinit var fileStorage: DownloadFileStorage
    private lateinit var tempDir: java.io.File

    @Before
    fun setUp() {
        tempDir = kotlin.io.path.createTempDirectory("pdf-downloader-test").toFile()
        fileStorage = DownloadFileStorage(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun validPdfResponseCompletesAndWritesTheFile() = runTest {
        val pdfBytes = "%PDF-1.4\n...".toByteArray(Charsets.US_ASCII)
        val downloader = PdfDownloader(
            fetcher = { PdfHttpResponse(statusCode = 200, contentLength = pdfBytes.size.toLong(), body = pdfBytes) },
            fileStorage = fileStorage,
        )

        val outcome = downloader.download("d1", "https://demo.paperreader.invalid/papers/W1.pdf")

        assertTrue(outcome is DownloadOutcome.Completed)
        val completed = outcome as DownloadOutcome.Completed
        assertEquals(pdfBytes.size.toLong(), completed.totalBytes)
        assertTrue(completed.file.readBytes().contentEquals(pdfBytes))
    }

    @Test
    fun htmlResponseFailsWithoutWritingAFile() = runTest {
        val htmlBytes = "<html>Not found</html>".toByteArray(Charsets.US_ASCII)
        val downloader = PdfDownloader(
            fetcher = { PdfHttpResponse(statusCode = 200, contentLength = htmlBytes.size.toLong(), body = htmlBytes) },
            fileStorage = fileStorage,
        )

        val outcome = downloader.download("d2", "https://demo.paperreader.invalid/papers/W2.pdf")

        assertTrue(outcome is DownloadOutcome.Failed)
        assertTrue(fileStorage.fileFor("d2", "pdf").exists().not())
    }

    @Test
    fun networkFailureIsReportedAsFailed() = runTest {
        val downloader = PdfDownloader(
            fetcher = { throw IOException("connection reset") },
            fileStorage = fileStorage,
        )

        val outcome = downloader.download("d3", "https://demo.paperreader.invalid/papers/W3.pdf")

        assertTrue(outcome is DownloadOutcome.Failed)
    }
}
