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

    suspend fun retry(downloadId: String): RepositoryResult<DownloadRecord>

    suspend fun cancel(downloadId: String): RepositoryResult<Unit>
}
