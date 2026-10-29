package com.example.paperReader.domain.usecase

import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPaperUseCaseTest {
    private val downloads = FakeDownloadsRepository()
    private val presentFiles = mutableSetOf<String>()
    private val useCase = ReadPaperUseCase(downloads, DownloadedFiles { it in presentFiles })
    private val paper = TestFixtures.paper("W1", access = TestFixtures.directPdf())

    @Test
    fun aCompletedPaperWithItsFileOpensWithoutStartingATransfer() = runTest {
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = "content://app/W1.pdf")))
        presentFiles += "content://app/W1.pdf"

        assertEquals(RepositoryResult.Success("W1"), useCase(paper))
        assertTrue("no confirmation call, so nothing reaches the network", downloads.startDownloadCalls.isEmpty())
    }

    @Test
    fun aRunningOrQueuedTransferIsFollowedNotDuplicated() = runTest {
        for (state in listOf(DownloadState.QUEUED, DownloadState.RUNNING)) {
            downloads.setDownloads(listOf(record(state)))

            assertEquals(RepositoryResult.Success("W1"), useCase(paper))
        }
        assertTrue(downloads.startDownloadCalls.isEmpty())
    }

    @Test
    fun anythingElseStartsAConfirmedTransfer() = runTest {
        downloads.startResult = RepositoryResult.Success(record(DownloadState.QUEUED))
        val cases = listOf(
            emptyList(),
            listOf(record(DownloadState.FAILED)),
            listOf(record(DownloadState.CANCELED)),
            // Completed, but the file it names is gone.
            listOf(record(DownloadState.COMPLETED, uri = "content://app/W1.pdf")),
        )

        for (records in cases) {
            downloads.setDownloads(records)
            assertEquals(RepositoryResult.Success("W1"), useCase(paper))
        }
        assertEquals(List(cases.size) { paper }, downloads.startDownloadCalls)
    }

    @Test
    fun aFailedConfirmationAnswersTheFailureAndNoRecord() = runTest {
        downloads.startResult = RepositoryResult.Failure(RepositoryError.NoDownloadableContent)

        assertEquals(RepositoryResult.Failure(RepositoryError.NoDownloadableContent), useCase(paper))
    }

    @Test
    fun aPaperWhoseAccessIsNoLongerConfirmedOpensNothingEvenWithItsFilePresent() = runTest {
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = "content://app/W1.pdf")))
        presentFiles += "content://app/W1.pdf"

        for (access in listOf(AccessResolution.Unknown, AccessResolution.Checking, AccessResolution.Paywalled)) {
            val result = useCase(paper.copy(access = access))

            assertTrue(result is RepositoryResult.Failure)
        }
        assertTrue(downloads.startDownloadCalls.isEmpty())
    }

    @Test
    fun aSecondTapWhileTheFirstIsDecidingDoesNotStartASecondTransfer() = runTest {
        val confirmation = CompletableDeferred<Unit>()
        val slowDownloads = object : com.example.paperReader.domain.repository.DownloadsRepository by downloads {
            override suspend fun startDownload(paper: com.example.paperReader.domain.model.Paper): RepositoryResult<DownloadRecord> {
                confirmation.await()
                return downloads.startDownload(paper).also { downloads.setDownloads(listOf(record(DownloadState.QUEUED))) }
            }
        }
        downloads.startResult = RepositoryResult.Success(record(DownloadState.QUEUED))
        val doubleTapped = ReadPaperUseCase(slowDownloads, DownloadedFiles { false })

        val first = async { doubleTapped(paper) }
        val second = async { doubleTapped(paper) }
        confirmation.complete(Unit)

        assertEquals(RepositoryResult.Success("W1"), first.await())
        assertEquals(RepositoryResult.Success("W1"), second.await())
        assertEquals("the second tap found the first one's record", 1, downloads.startDownloadCalls.size)
    }

    private fun record(state: DownloadState, uri: String? = null) = DownloadRecord(
        id = "W1",
        paperId = PaperId("W1"),
        title = "Demo title",
        state = state,
        localContentUri = uri,
        mimeType = "application/pdf",
        updatedAtEpochMillis = 0,
    )
}
