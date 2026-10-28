package com.example.paperReader.data.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.testPaper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The production gateway is a transport boundary: it trusts the descriptor it is handed, and its
 * job is to make sure the transfer that gets scheduled uses *that* descriptor (AND-006), while one
 * paper still resolves to one work chain no matter how often the user taps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerDownloadGatewayTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: PaperReaderDatabase
    private lateinit var workManager: WorkManager
    private lateinit var fileStorage: DownloadFileStorage
    private lateinit var gateway: WorkManagerDownloadGateway

    private val paper = testPaper("W1")
    private val original = ConfirmedPdfDescriptor(
        url = "https://cdn.example.org/W1-original.pdf",
        mimeType = "application/pdf",
    )
    private val confirmed = ConfirmedPdfDescriptor(
        url = "https://cdn.example.org/W1-confirmed.pdf",
        mimeType = "application/pdf; qualified",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        database = Room.inMemoryDatabaseBuilder(context, PaperReaderDatabase::class.java)
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
        fileStorage = DownloadFileStorage(temporaryFolder.newFolder("downloads"))
        gateway = WorkManagerDownloadGateway(workManager, database.downloadDao(), fileStorage, FakeClock(0L))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearCompletedRemovesCompletedRecordsAndTheirFilesTogether() = runTest {
        gateway.enqueue(paper, original)
        gateway.enqueue(testPaper("W2"), original)
        val dao = database.downloadDao()
        dao.upsert(dao.get("W1")!!.copy(state = DownloadState.COMPLETED))
        dao.upsert(dao.get("W2")!!.copy(state = DownloadState.FAILED))
        val completedFile = fileStorage.write("W1", extension = "pdf", bytes = byteArrayOf(1, 2, 3))
        val failedFile = fileStorage.write("W2", extension = "pdf", bytes = byteArrayOf(4))

        gateway.clearCompleted()

        // Record and file go together: neither is left pointing at the other.
        assertEquals(null, dao.get("W1"))
        assertFalse("The completed file outlived its record", completedFile.exists())
        // Nothing that still needs a decision is touched.
        assertEquals(DownloadState.FAILED, dao.get("W2")?.state)
        assertTrue("A failed record's file was deleted with the completed ones", failedFile.exists())
    }

    @Test
    fun clearCompletedLeavesInProgressRecordsAlone() = runTest {
        gateway.enqueue(paper, original)

        gateway.clearCompleted()

        assertEquals(DownloadState.QUEUED, database.downloadDao().get("W1")?.state)
    }

    @Test
    fun enqueueRecordsTheDescriptorItWasGiven() = runTest {
        val result = gateway.enqueue(paper, original)

        assertTrue(result is RepositoryResult.Success)
        val stored = database.downloadDao().get(paper.id.value)
        assertEquals(original.url, stored?.sourceUrl)
        assertEquals(DownloadState.QUEUED, stored?.state)
    }

    @Test
    fun retryReplacesTheStoredUrlAndMimeTypeBeforeSchedulingWork() = runTest {
        gateway.enqueue(paper, original)

        gateway.retry(paper.id.value, confirmed)

        val stored = database.downloadDao().get(paper.id.value)
        assertEquals(
            "A retry must transfer from the location the backend just confirmed",
            confirmed.url,
            stored?.sourceUrl,
        )
        assertEquals(confirmed.mimeType, stored?.mimeType)
        assertEquals(DownloadState.QUEUED, stored?.state)
    }

    @Test
    fun retryingAnUnknownRecordFailsWithoutSchedulingAnything() = runTest {
        val result = gateway.retry("missing", confirmed)

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(workInfos("missing").isEmpty())
    }

    @Test
    fun repeatedEnqueuesForOnePaperStillResolveToOneWorkChain() = runTest {
        gateway.enqueue(paper, original)
        gateway.enqueue(paper, original)
        gateway.retry(paper.id.value, confirmed)

        assertEquals(1, workInfos(paper.id.value).size)
        assertEquals(1, database.downloadDao().observeAll().first().size)
    }

    private suspend fun workInfos(downloadId: String): List<WorkInfo> {
        return workManager.getWorkInfosForUniqueWorkFlow("pdf-download-$downloadId").first()
    }
}
