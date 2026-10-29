package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** No-op [DownloadsRepository] test double for ViewModel tests that only need to prove a screen
 * *calls* into downloads (e.g. "Read PDF" wiring) without exercising the download lifecycle
 * itself — that lifecycle has its own dedicated tests. */
class FakeDownloadsRepository : DownloadsRepository {
    val startDownloadCalls = mutableListOf<Paper>()
    val retryCalls = mutableListOf<String>()
    val cancelCalls = mutableListOf<String>()
    var clearCompletedCalls = 0
        private set

    /** What a start/retry attempt resolves to. Transfer-time confirmation means these calls really
     * can fail, and a ViewModel must surface that rather than drop it. */
    var startResult: RepositoryResult<DownloadRecord> = RepositoryResult.Failure(RepositoryError.Unexpected)
    var retryResult: RepositoryResult<DownloadRecord> = RepositoryResult.Failure(RepositoryError.Unexpected)
    var clearCompletedResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    private val downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())

    fun setDownloads(records: List<DownloadRecord>) {
        downloads.value = records
    }

    override fun observeDownloads(): Flow<List<DownloadRecord>> = downloads

    override suspend fun startDownload(paper: Paper): RepositoryResult<DownloadRecord> {
        startDownloadCalls += paper
        return startResult
    }

    override suspend fun retryDownload(downloadId: String): RepositoryResult<DownloadRecord> {
        retryCalls += downloadId
        return retryResult
    }

    override suspend fun cancelDownload(downloadId: String): RepositoryResult<Unit> {
        cancelCalls += downloadId
        return RepositoryResult.Success(Unit)
    }

    override suspend fun clearCompletedDownloads(): RepositoryResult<Unit> {
        clearCompletedCalls++
        // The real repository removes the rows; the double mirrors that so an observing ViewModel
        // sees the same list the user would.
        downloads.value = downloads.value.filterNot { it.state == DownloadState.COMPLETED }
        return clearCompletedResult
    }
}

/** A record as the repository would hold it; ViewModel tests only care about its id and state. */
fun testDownloadRecord(
    paperId: String,
    state: DownloadState = DownloadState.QUEUED,
    id: String = "download-$paperId",
) = DownloadRecord(
    id = id,
    paperId = PaperId(paperId),
    title = "Paper $paperId",
    state = state,
    mimeType = "application/pdf",
    updatedAtEpochMillis = 0L,
)
