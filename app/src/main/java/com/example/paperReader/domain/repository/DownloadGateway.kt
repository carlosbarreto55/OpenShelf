package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.Paper
import kotlinx.coroutines.flow.Flow

interface DownloadGateway {
    fun observeDownloads(): Flow<List<DownloadRecord>>

    suspend fun enqueue(
        paper: Paper,
        descriptor: ConfirmedPdfDescriptor,
    ): RepositoryResult<DownloadRecord>

    /**
     * [descriptor] is the descriptor the backend returned moments ago, not the one stored with the
     * record. A retry must transfer from the current location, so the gateway replaces whatever it
     * had persisted before scheduling work (business decision 5).
     */
    suspend fun retry(
        downloadId: String,
        descriptor: ConfirmedPdfDescriptor,
    ): RepositoryResult<DownloadRecord>

    suspend fun cancel(downloadId: String): RepositoryResult<Unit>

    /**
     * Removes every completed record together with the file it manages. Both or neither: a record
     * without its file, or a file without its record, is exactly the leak this exists to prevent.
     * In-progress and failed records are left untouched, as is saved-paper state, which this
     * gateway does not own.
     */
    suspend fun clearCompleted(): RepositoryResult<Unit>
}
