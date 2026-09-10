package com.example.paperReader.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.dao.DownloadDao
import com.example.paperReader.domain.model.DownloadState

/** Streams one confirmed PDF descriptor to app-managed storage. Inactive until a real backend
 * base URL is configured (see `BackendConfiguration`/`DownloadModule`) — [WorkManagerDownloadGateway]
 * is what actually schedules it. */
class PdfDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val downloader: PdfDownloader,
    private val contentUriProvider: ContentUriProvider,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val existing = downloadDao.get(downloadId) ?: return Result.failure()

        downloadDao.upsert(
            existing.copy(state = DownloadState.RUNNING, errorMessage = null, updatedAtEpochMillis = clock.nowEpochMillis()),
        )

        return when (val outcome = downloader.download(downloadId, url)) {
            is DownloadOutcome.Completed -> {
                val contentUri = contentUriProvider.uriFor(outcome.file)
                downloadDao.upsert(
                    existing.copy(
                        state = DownloadState.COMPLETED,
                        bytesDownloaded = outcome.totalBytes,
                        totalBytes = outcome.totalBytes,
                        localContentUri = contentUri,
                        mimeType = "application/pdf",
                        errorMessage = null,
                        updatedAtEpochMillis = clock.nowEpochMillis(),
                    ),
                )
                Result.success()
            }

            is DownloadOutcome.Failed -> {
                downloadDao.upsert(
                    existing.copy(
                        state = DownloadState.FAILED,
                        errorMessage = outcome.message,
                        updatedAtEpochMillis = clock.nowEpochMillis(),
                    ),
                )
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "downloadId"
        const val KEY_URL = "url"
    }
}
