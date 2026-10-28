package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.Paper
import kotlinx.coroutines.flow.Flow

/**
 * Sits above [DownloadGateway]: the gateway is a pure transport boundary (fake or
 * WorkManager-backed) that trusts whatever descriptor it is given, while this repository is the
 * fail-closed gate — it only ever starts or retries a transfer while the paper's *current* access
 * resolution is still a confirmed `DIRECT_PDF`.
 */
interface DownloadsRepository {
    fun observeDownloads(): Flow<List<DownloadRecord>>

    suspend fun startDownload(paper: Paper): RepositoryResult<DownloadRecord>

    suspend fun retryDownload(downloadId: String): RepositoryResult<DownloadRecord>

    suspend fun cancelDownload(downloadId: String): RepositoryResult<Unit>

    /** Removes completed records and their managed local files together. Leaves in-progress and
     * failed records, and saved-paper state, exactly as they were. */
    suspend fun clearCompletedDownloads(): RepositoryResult<Unit>
}
