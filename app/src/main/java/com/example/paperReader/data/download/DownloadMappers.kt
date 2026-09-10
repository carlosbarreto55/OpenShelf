package com.example.paperReader.data.download

import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.domain.model.DownloadProgress
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.PaperId

fun DownloadEntity.toDomain(): DownloadRecord {
    return DownloadRecord(
        id = id,
        paperId = PaperId(paperId),
        title = title,
        state = state,
        progress = bytesDownloaded?.let { DownloadProgress(bytesDownloaded = it, totalBytes = totalBytes) },
        localContentUri = localContentUri,
        mimeType = mimeType,
        errorMessage = errorMessage,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
