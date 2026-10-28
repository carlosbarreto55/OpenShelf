package com.example.paperReader.data.download

import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.dao.DownloadDao
import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.di.ApplicationScope
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.DownloadGateway
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Default active [DownloadGateway] while no backend exists: simulates queue/running/progress
 * transitions and persists them through the same [DownloadDao] rows the UI observes, so the
 * lifecycle is indistinguishable from a real one except for its content. It never fabricates a
 * PDF — the "completed" file is a plain-text disclaimer, not a document that could pass for the
 * requested paper (see the paper-downloads spec's honest-placeholder requirement).
 */
class FakeDownloadManager @Inject constructor(
    private val downloadDao: DownloadDao,
    private val fileStorage: DownloadFileStorage,
    private val contentUriProvider: ContentUriProvider,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) : DownloadGateway {

    /** Test/demo hook: when non-null, the next simulated download fails with this message instead
     * of completing, then the flag resets. */
    var failNextWithMessage: String? = null

    var queueDelayMillis: Long = 300L
    var stepDelayMillis: Long = 250L

    private val jobs = ConcurrentHashMap<String, Job>()

    override fun observeDownloads(): Flow<List<DownloadRecord>> {
        return downloadDao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun enqueue(paper: Paper, descriptor: ConfirmedPdfDescriptor): RepositoryResult<DownloadRecord> {
        val downloadId = paper.id.value
        downloadDao.upsert(
            DownloadEntity(
                id = downloadId,
                paperId = paper.id.value,
                title = paper.title,
                state = DownloadState.QUEUED,
                sourceUrl = descriptor.url,
                mimeType = descriptor.mimeType,
                bytesDownloaded = 0L,
                totalBytes = SIMULATED_TOTAL_BYTES,
                localContentUri = null,
                errorMessage = null,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
        simulate(downloadId, paper.title)
        return currentRecord(downloadId)
    }

    override suspend fun retry(
        downloadId: String,
        descriptor: ConfirmedPdfDescriptor,
    ): RepositoryResult<DownloadRecord> {
        val existing = downloadDao.get(downloadId)
            ?: return RepositoryResult.Failure(RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE))

        // Demo mode still goes through confirmation and still records the confirmed descriptor;
        // only the transfer itself is simulated, and it still never fabricates a PDF.
        downloadDao.upsert(
            existing.copy(
                state = DownloadState.QUEUED,
                sourceUrl = descriptor.url,
                mimeType = descriptor.mimeType,
                bytesDownloaded = 0L,
                totalBytes = SIMULATED_TOTAL_BYTES,
                localContentUri = null,
                errorMessage = null,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
        simulate(downloadId, existing.title)
        return currentRecord(downloadId)
    }

    override suspend fun cancel(downloadId: String): RepositoryResult<Unit> {
        jobs.remove(downloadId)?.cancel()
        fileStorage.delete(downloadId)
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

    override suspend fun clearCompleted(): RepositoryResult<Unit> {
        val completed = downloadDao.observeAll().first().filter { it.state == DownloadState.COMPLETED }
        for (entity in completed) {
            fileStorage.delete(entity.id)
            downloadDao.delete(entity.id)
        }
        return RepositoryResult.Success(Unit)
    }

    private suspend fun currentRecord(downloadId: String): RepositoryResult<DownloadRecord> {
        return downloadDao.get(downloadId)?.toDomain()?.let { RepositoryResult.Success(it) }
            ?: RepositoryResult.Failure(RepositoryError.Unexpected)
    }

    private fun simulate(downloadId: String, title: String) {
        jobs[downloadId]?.cancel()
        val failureMessage = failNextWithMessage
        failNextWithMessage = null

        jobs[downloadId] = scope.launch {
            delay(queueDelayMillis)
            val running = downloadDao.get(downloadId) ?: return@launch
            if (running.state != DownloadState.QUEUED) return@launch // canceled while queued
            downloadDao.upsert(running.copy(state = DownloadState.RUNNING, updatedAtEpochMillis = clock.nowEpochMillis()))

            repeat(PROGRESS_STEPS) { step ->
                delay(stepDelayMillis)
                val current = downloadDao.get(downloadId) ?: return@launch
                if (current.state != DownloadState.RUNNING) return@launch // canceled mid-transfer
                val bytes = SIMULATED_TOTAL_BYTES * (step + 1) / PROGRESS_STEPS
                downloadDao.upsert(current.copy(bytesDownloaded = bytes, updatedAtEpochMillis = clock.nowEpochMillis()))
            }

            val finished = downloadDao.get(downloadId) ?: return@launch
            if (finished.state != DownloadState.RUNNING) return@launch

            if (failureMessage != null) {
                downloadDao.upsert(
                    finished.copy(state = DownloadState.FAILED, errorMessage = failureMessage, updatedAtEpochMillis = clock.nowEpochMillis()),
                )
            } else {
                val file = fileStorage.write(downloadId, extension = "txt", bytes = demoDisclaimer(title).toByteArray())
                val contentUri = contentUriProvider.uriFor(file)
                downloadDao.upsert(
                    finished.copy(
                        state = DownloadState.COMPLETED,
                        bytesDownloaded = SIMULATED_TOTAL_BYTES,
                        localContentUri = contentUri,
                        mimeType = "text/plain",
                        updatedAtEpochMillis = clock.nowEpochMillis(),
                    ),
                )
            }
            jobs.remove(downloadId)
        }
    }

    private fun demoDisclaimer(title: String): String {
        return """
            Demo download placeholder

            This is not the PDF for "$title".

            No live PaperReader backend is connected yet, so nothing was actually retrieved. This
            file only demonstrates the download lifecycle (queued, running, completed) without
            fabricating content that could be mistaken for the requested paper.
        """.trimIndent()
    }

    private companion object {
        const val PROGRESS_STEPS = 5
        const val SIMULATED_TOTAL_BYTES = 512_000L
    }
}
