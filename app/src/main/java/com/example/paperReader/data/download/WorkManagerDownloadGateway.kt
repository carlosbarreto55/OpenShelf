package com.example.paperReader.data.download

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.dao.DownloadDao
import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.DownloadGateway
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Production [DownloadGateway]: one WorkManager unique work per paper, backed by the same
 * [DownloadDao] rows the UI observes. Inactive until [com.example.paperReader.core.config.BackendConfiguration]
 * selects it (see `DownloadModule`) — like `HttpPaperRemoteDataSource`, it must compile and behave
 * correctly but need not be reachable without a real backend base URL. */
class WorkManagerDownloadGateway @Inject constructor(
    private val workManager: WorkManager,
    private val downloadDao: DownloadDao,
    private val clock: Clock,
) : DownloadGateway {

    override fun observeDownloads(): Flow<List<DownloadRecord>> {
        return downloadDao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun enqueue(paper: Paper, descriptor: ConfirmedPdfDescriptor): RepositoryResult<DownloadRecord> {
        val downloadId = paper.id.value
        val now = clock.nowEpochMillis()
        downloadDao.upsert(
            DownloadEntity(
                id = downloadId,
                paperId = paper.id.value,
                title = paper.title,
                state = DownloadState.QUEUED,
                sourceUrl = descriptor.url,
                mimeType = descriptor.mimeType,
                bytesDownloaded = null,
                totalBytes = null,
                localContentUri = null,
                errorMessage = null,
                updatedAtEpochMillis = now,
            ),
        )
        scheduleWork(downloadId, descriptor.url)
        return downloadDao.get(downloadId)?.toDomain()?.let { RepositoryResult.Success(it) }
            ?: RepositoryResult.Failure(RepositoryError.Unexpected)
    }

    override suspend fun retry(
        downloadId: String,
        descriptor: ConfirmedPdfDescriptor,
    ): RepositoryResult<DownloadRecord> {
        val existing = downloadDao.get(downloadId)
            ?: return RepositoryResult.Failure(RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE))

        // The freshly confirmed descriptor replaces the stored one *before* work is scheduled, so
        // the worker can never pick up a URL the backend has already moved on from.
        downloadDao.upsert(
            existing.copy(
                state = DownloadState.QUEUED,
                sourceUrl = descriptor.url,
                mimeType = descriptor.mimeType,
                errorMessage = null,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
        scheduleWork(downloadId, descriptor.url)
        return downloadDao.get(downloadId)?.toDomain()?.let { RepositoryResult.Success(it) }
            ?: RepositoryResult.Failure(RepositoryError.Unexpected)
    }

    override suspend fun cancel(downloadId: String): RepositoryResult<Unit> {
        workManager.cancelUniqueWork(uniqueWorkName(downloadId))
        val existing = downloadDao.get(downloadId)
        if (existing != null) {
            downloadDao.upsert(
                existing.copy(
                    state = DownloadState.CANCELED,
                    localContentUri = null,
                    errorMessage = null,
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                ),
            )
        }
        return RepositoryResult.Success(Unit)
    }

    /** Called once at startup for any backend-mode build: a download left QUEUED/RUNNING when the
     * process died has no live WorkManager entry to resume, so it is marked failed and retryable
     * rather than shown as progressing forever. */
    suspend fun reconcileInterruptedDownloads() {
        val interrupted = downloadDao.observeAll().first()
            .filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }

        for (entity in interrupted) {
            val workInfos = workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(entity.id)).first()
            val stillActive = workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            if (!stillActive) {
                downloadDao.upsert(
                    entity.copy(
                        state = DownloadState.FAILED,
                        errorMessage = "Interrupted before it could finish. Try again.",
                        updatedAtEpochMillis = clock.nowEpochMillis(),
                    ),
                )
            }
        }
    }

    private fun scheduleWork(downloadId: String, url: String) {
        val request = OneTimeWorkRequestBuilder<PdfDownloadWorker>()
            .setInputData(
                workDataOf(
                    PdfDownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                    PdfDownloadWorker.KEY_URL to url,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(downloadId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueWorkName(downloadId: String) = "pdf-download-$downloadId"
}
