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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
    private lateinit var database: PaperReaderDatabase
    private lateinit var workManager: WorkManager
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
        gateway = WorkManagerDownloadGateway(workManager, database.downloadDao(), FakeClock(0L))
    }

    @After
    fun tearDown() {
        database.close()
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
