package com.example.paperReader.data.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.repository.RepositoryResult
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses [runBlocking], not `runTest`: [FakeDownloadManager] simulates progress on a real background
 * dispatcher, and `runTest`'s virtual clock would race ahead of that real work instead of waiting
 * for it. Delays are set to 0 so real wall-clock time stays negligible regardless.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FakeDownloadManagerTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var tempDir: java.io.File
    private lateinit var manager: FakeDownloadManager

    private val paper = TestFixtures.paper("W1")
    private val descriptor = ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W1.pdf", mimeType = "application/pdf")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        tempDir = kotlin.io.path.createTempDirectory("fake-download-test").toFile()
        manager = FakeDownloadManager(
            downloadDao = database.downloadDao(),
            fileStorage = DownloadFileStorage(tempDir),
            contentUriProvider = ContentUriProvider { file -> "content://fake/${file.name}" },
            clock = FakeClock(0L),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        manager.queueDelayMillis = 0
        manager.stepDelayMillis = 0
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun enqueueReachesCompletedWithoutWritingAPdf() = runBlocking {
        manager.enqueue(paper, descriptor)

        val record = awaitTerminalState()
        assertEquals(DownloadState.COMPLETED, record.state)
        assertTrue(record.mimeType != "application/pdf") // never fabricates a paper PDF
        assertEquals("text/plain", record.mimeType)
    }

    @Test
    fun cancelStopsTheSimulationAndRemovesAnyPartialFile() = runBlocking {
        manager.queueDelayMillis = 10_000 // long enough that it is still QUEUED when we cancel
        manager.enqueue(paper, descriptor)

        val downloadId = paper.id.value
        val result = manager.cancel(downloadId)

        assertTrue(result is RepositoryResult.Success)
        val record = manager.observeDownloads().first().single()
        assertEquals(DownloadState.CANCELED, record.state)
        assertFalse(DownloadFileStorage(tempDir).fileFor(downloadId, "txt").exists())
    }

    @Test
    fun retryAfterFailureReachesCompletedUsingTheFreshlyConfirmedDescriptor() = runBlocking {
        manager.failNextWithMessage = "Simulated failure"
        manager.enqueue(paper, descriptor)
        assertEquals(DownloadState.FAILED, awaitTerminalState().state)

        // Demo mode goes through the same transfer-time confirmation, so retry is handed a
        // descriptor rather than reusing the one stored with the record.
        val confirmed = descriptor.copy(url = "https://demo.paperreader.invalid/papers/W1-fresh.pdf")
        manager.retry(paper.id.value, confirmed)

        assertEquals(DownloadState.COMPLETED, awaitTerminalState().state)
        assertEquals(confirmed.url, database.downloadDao().get(paper.id.value)?.sourceUrl)
    }

    /** Polls the DAO directly rather than collecting [FakeDownloadManager.observeDownloads]: the
     * simulation runs on a real background dispatcher, and Room's Flow invalidation notifications
     * are posted through the main-thread executor, which Robolectric does not pump on its own. */
    private suspend fun awaitTerminalState() = withTimeout(5.seconds) {
        var record = database.downloadDao().get(paper.id.value)
        while (record == null || record.state !in setOf(DownloadState.COMPLETED, DownloadState.FAILED)) {
            delay(10)
            record = database.downloadDao().get(paper.id.value)
        }
        record.toDomain()
    }
}
