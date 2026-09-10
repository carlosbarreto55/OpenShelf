package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.Paper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** No-op [DownloadsRepository] test double for ViewModel tests that only need to prove a screen
 * *calls* into downloads (e.g. "Download PDF" wiring) without exercising the download lifecycle
 * itself — that lifecycle has its own dedicated tests. */
class FakeDownloadsRepository : DownloadsRepository {
    val startDownloadCalls = mutableListOf<Paper>()
    val retryCalls = mutableListOf<String>()
    val cancelCalls = mutableListOf<String>()

    /** What a start/retry attempt resolves to. Transfer-time confirmation means these calls really
     * can fail, and a ViewModel must surface that rather than drop it. */
    var startResult: RepositoryResult<DownloadRecord> = RepositoryResult.Failure(RepositoryError.Unexpected)
    var retryResult: RepositoryResult<DownloadRecord> = RepositoryResult.Failure(RepositoryError.Unexpected)

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
}
