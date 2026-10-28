package com.example.paperReader.data.repository

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.DownloadGateway
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.paperdetail.FakePaperDetailRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two gates the paper-downloads spec requires:
 *
 *  1. a *new* user action still needs the paper's current access to be a confirmed DIRECT_PDF, and
 *  2. every transfer, initial or retried, is authorized by a descriptor the backend returned
 *     moments ago — never by the one cached in the paper or the download record (AND-006).
 */
class DownloadsRepositoryImplTest {
    private val cachedDescriptor = ConfirmedPdfDescriptor(url = "https://cdn.example.org/W1-cached.pdf")
    private val freshDescriptor = ConfirmedPdfDescriptor(
        url = "https://cdn.example.org/W1-fresh.pdf",
        mimeType = "application/pdf",
    )

    @Test
    fun startDownloadEnqueuesTheFreshlyConfirmedDescriptorNotTheCachedOne() = runTest {
        val paper = testPaper("W1", access = AccessResolution.DirectPdf(cachedDescriptor))
        val gateway = FakeDownloadGateway()
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Available(freshDescriptor)))

        val result = DownloadsRepositoryImpl(gateway, papers).startDownload(paper)

        assertTrue(result is RepositoryResult.Success)
        assertEquals(listOf(paper to freshDescriptor), gateway.enqueueCalls)
        assertEquals(listOf(paper.id), papers.confirmDownloadCalls)
    }

    @Test
    fun startDownloadRejectsAnInaccessiblePaperWithoutContactingTheBackend() = runTest {
        val paper = testPaper("W1", access = AccessResolution.Paywalled)
        val gateway = FakeDownloadGateway()
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Available(freshDescriptor)))

        val result = DownloadsRepositoryImpl(gateway, papers).startDownload(paper)

        assertEquals(
            RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR),
            (result as RepositoryResult.Failure).error,
        )
        assertTrue(gateway.enqueueCalls.isEmpty())
        assertTrue("A paper with no confirmed action must not be looked up", papers.confirmDownloadCalls.isEmpty())
    }

    @Test
    fun everyNonAuthorizingConfirmationOutcomeEnqueuesNothingAndNeverFallsBackToTheCachedUrl() = runTest {
        val outcomes = mapOf(
            "no PDF" to RepositoryResult.Success(DownloadAvailability.Unavailable),
            "not found" to RepositoryResult.Failure(RepositoryError.NotFound),
            "offline" to RepositoryResult.Failure(RepositoryError.Offline),
            "timeout" to RepositoryResult.Failure(RepositoryError.Timeout),
            "upstream unavailable" to RepositoryResult.Failure(RepositoryError.ServiceUnavailable),
            "mismatched identity" to RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
            ),
            "malformed body" to RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE),
            ),
            "expired descriptor" to RepositoryResult.Failure(
                RepositoryError.AccessValidation(RepositoryError.AccessFailure.EXPIRED_DESCRIPTOR),
            ),
        )

        outcomes.forEach { (name, confirmation) ->
            val paper = testPaper("W1", access = AccessResolution.DirectPdf(cachedDescriptor))
            val gateway = FakeDownloadGateway()

            val result = DownloadsRepositoryImpl(gateway, repositoryConfirming(confirmation)).startDownload(paper)

            assertTrue("$name must fail", result is RepositoryResult.Failure)
            assertTrue("$name must enqueue nothing", gateway.enqueueCalls.isEmpty())
        }
    }

    @Test
    fun noPdfIsReportedAsItsOwnOutcomeRatherThanMalformedData() = runTest {
        val paper = testPaper("W1", access = AccessResolution.DirectPdf(cachedDescriptor))
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Unavailable))

        val result = DownloadsRepositoryImpl(FakeDownloadGateway(), papers).startDownload(paper)

        assertEquals(RepositoryError.NoDownloadableContent, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun retryConfirmsWithTheBackendAndUsesTheNewlyReturnedUrl() = runTest {
        val paper = testPaper("W1", access = AccessResolution.DirectPdf(cachedDescriptor))
        val gateway = FakeDownloadGateway().apply { setDownloads(listOf(record(paper.id, DownloadState.FAILED))) }
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Available(freshDescriptor)))

        val result = DownloadsRepositoryImpl(gateway, papers).retryDownload(paper.id.value)

        assertTrue(result is RepositoryResult.Success)
        assertEquals(listOf(paper.id.value to freshDescriptor), gateway.retryCalls)
        assertEquals(listOf(paper.id), papers.confirmDownloadCalls)
    }

    @Test
    fun retryConfirmsEvenWhenTheCachedAccessSnapshotIsNoLongerActionable() = runTest {
        // The record exists and the endpoint is the freshness authority, so a stale cached
        // snapshot must not block a retry the backend is willing to authorize.
        val paper = testPaper("W1", access = AccessResolution.Unknown)
        val gateway = FakeDownloadGateway().apply { setDownloads(listOf(record(paper.id, DownloadState.FAILED))) }
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Available(freshDescriptor)))

        val result = DownloadsRepositoryImpl(gateway, papers).retryDownload(paper.id.value)

        assertTrue(result is RepositoryResult.Success)
        assertEquals(listOf(paper.id.value to freshDescriptor), gateway.retryCalls)
    }

    @Test
    fun retryStopsWhenConfirmationFailsAndLeavesTheRecordAlone() = runTest {
        val paperId = PaperId("W1")
        val existing = record(paperId, DownloadState.FAILED)
        val gateway = FakeDownloadGateway().apply { setDownloads(listOf(existing)) }
        val papers = repositoryConfirming(RepositoryResult.Failure(RepositoryError.NotFound))

        val result = DownloadsRepositoryImpl(gateway, papers).retryDownload(paperId.value)

        assertEquals(RepositoryError.NotFound, (result as RepositoryResult.Failure).error)
        assertTrue(gateway.retryCalls.isEmpty())
        assertEquals(listOf(existing), gateway.currentDownloads())
    }

    @Test
    fun retryDownloadForAnUnknownIdFailsWithContractError() = runTest {
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Available(freshDescriptor)))

        val result = DownloadsRepositoryImpl(FakeDownloadGateway(), papers).retryDownload("missing")

        assertTrue((result as RepositoryResult.Failure).error is RepositoryError.Contract)
        assertTrue("An unknown record must not reach the backend", papers.confirmDownloadCalls.isEmpty())
    }

    @Test
    fun cancelDownloadDelegatesDirectlyToTheGateway() = runTest {
        val gateway = FakeDownloadGateway()
        val repository = DownloadsRepositoryImpl(gateway, FakePaperDetailRepository())

        repository.cancelDownload("d1")

        assertEquals(listOf("d1"), gateway.cancelCalls)
    }

    @Test
    fun clearCompletedGoesThroughTheGatewayAndNeverTouchesThePaperSide() = runTest {
        val gateway = FakeDownloadGateway()
        gateway.setDownloads(
            listOf(
                record(PaperId("W1"), DownloadState.COMPLETED),
                record(PaperId("W2"), DownloadState.FAILED),
            ),
        )
        val papers = repositoryConfirming(RepositoryResult.Success(DownloadAvailability.Unavailable))

        val result = DownloadsRepositoryImpl(gateway, papers).clearCompletedDownloads()

        assertTrue(result is RepositoryResult.Success)
        assertEquals(1, gateway.clearCompletedCalls)
        assertEquals(listOf(PaperId("W2")), gateway.currentDownloads().map { it.paperId })
        // Saved papers are a different capability with different storage; clearing a download must
        // not reach into it, so nothing here is asked of the paper repository at all.
        assertTrue("Clearing downloads touched the paper side", papers.confirmDownloadCalls.isEmpty())
    }

    private fun repositoryConfirming(
        outcome: RepositoryResult<DownloadAvailability>,
    ): FakePaperDetailRepository = FakePaperDetailRepository().apply {
        confirmDownloadHandler = { outcome }
    }

    private class FakeDownloadGateway : DownloadGateway {
        val enqueueCalls = mutableListOf<Pair<Paper, ConfirmedPdfDescriptor>>()
        val retryCalls = mutableListOf<Pair<String, ConfirmedPdfDescriptor>>()
        val cancelCalls = mutableListOf<String>()
        private val downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())

        fun setDownloads(records: List<DownloadRecord>) {
            downloads.value = records
        }

        fun currentDownloads(): List<DownloadRecord> = downloads.value

        override fun observeDownloads(): Flow<List<DownloadRecord>> = downloads

        override suspend fun enqueue(paper: Paper, descriptor: ConfirmedPdfDescriptor): RepositoryResult<DownloadRecord> {
            enqueueCalls += paper to descriptor
            return RepositoryResult.Success(record(paper.id, DownloadState.QUEUED))
        }

        override suspend fun retry(
            downloadId: String,
            descriptor: ConfirmedPdfDescriptor,
        ): RepositoryResult<DownloadRecord> {
            retryCalls += downloadId to descriptor
            return RepositoryResult.Success(record(PaperId(downloadId), DownloadState.QUEUED))
        }

        var clearCompletedCalls = 0

        override suspend fun clearCompleted(): RepositoryResult<Unit> {
            clearCompletedCalls++
            downloads.value = downloads.value.filterNot { it.state == DownloadState.COMPLETED }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun cancel(downloadId: String): RepositoryResult<Unit> {
            cancelCalls += downloadId
            return RepositoryResult.Success(Unit)
        }
    }
}

private fun record(paperId: PaperId, state: DownloadState) = DownloadRecord(
    id = paperId.value,
    paperId = paperId,
    title = "Demo title",
    state = state,
    updatedAtEpochMillis = 0L,
)
