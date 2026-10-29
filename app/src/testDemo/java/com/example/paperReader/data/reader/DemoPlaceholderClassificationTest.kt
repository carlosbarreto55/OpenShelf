package com.example.paperReader.data.reader

import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.download.ContentUriProvider
import com.example.paperReader.data.download.DownloadFileStorage
import com.example.paperReader.data.download.FakeDownloadManager
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reader must never present demo output as the paper (design.md decision 5). This opens the
 * file [FakeDownloadManager] actually writes — not a stand-in shaped like it — so a change to the
 * placeholder that started to look like a PDF would fail here. That is also why it lives in
 * `testDemo`: the class does not exist in either HTTP flavor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoPlaceholderClassificationTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var dir: File
    private lateinit var manager: FakeDownloadManager

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dir = kotlin.io.path.createTempDirectory("demo-placeholder-test").toFile()
        manager = FakeDownloadManager(
            downloadDao = database.downloadDao(),
            fileStorage = DownloadFileStorage(dir),
            contentUriProvider = ContentUriProvider { file -> CONTENT_PREFIX + file.name },
            clock = FakeClock(0L),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        manager.queueDelayMillis = 0
        manager.stepDelayMillis = 0
    }

    @After
    fun tearDown() {
        database.close()
        dir.deleteRecursively()
    }

    @Test
    fun theDemoDownloadIsClassifiedAsNotAPdf() = runBlocking {
        val platform = FakePlatformDocuments()
        val source = PdfRendererPageSource(
            fileOpener = { uri ->
                ParcelFileDescriptor.open(File(dir, uri.removePrefix(CONTENT_PREFIX)), ParcelFileDescriptor.MODE_READ_ONLY)
            },
            documentFactory = platform,
            ioDispatcher = Dispatchers.IO,
        )
        val paper = TestFixtures.paper("W1")
        manager.enqueue(paper, ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/W1.pdf", mimeType = "application/pdf"))
        val completed = awaitCompleted()

        val result = source.open(checkNotNull(completed.localContentUri))
        source.close()

        assertEquals(PdfOpenResult.NotPdf, result)
        assertEquals("the placeholder never reaches the renderer", 0, platform.opened)
    }

    private suspend fun awaitCompleted(): DownloadRecord = withTimeout(5.seconds) {
        manager.observeDownloads().first { records -> records.any { it.state == DownloadState.COMPLETED } }.single()
    }

    private companion object {
        const val CONTENT_PREFIX = "content://fake/"
    }
}
