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
}
