package com.example.paperReader.data.repository

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.DownloadGateway
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Every transfer — initial or retried — is authorized by a descriptor the backend returned moments
 * ago, never by one embedded in a cached [Paper] or download record (business decision 5, AND-006).
 *
 * A new user action additionally keeps the fail-closed prerequisite that the paper's *current*
 * access is a confirmed `DIRECT_PDF`, so an action can only start from an affordance the user was
 * legitimately shown. Retry does not: the record already exists, and the download endpoint is the
 * freshness authority even when the cached access snapshot has since gone stale.
 */
class DownloadsRepositoryImpl @Inject constructor(
    private val downloadGateway: DownloadGateway,
    private val paperRepository: PaperRepository,
) : DownloadsRepository {

    override fun observeDownloads(): Flow<List<DownloadRecord>> = downloadGateway.observeDownloads()

    override suspend fun startDownload(paper: Paper): RepositoryResult<DownloadRecord> {
        if (paper.access !is AccessResolution.DirectPdf) {
            return accessNoLongerConfirmed()
        }
        return when (val confirmation = confirm(paper.id)) {
            is RepositoryResult.Failure -> confirmation
            is RepositoryResult.Success -> downloadGateway.enqueue(paper, confirmation.value.descriptor)
        }
    }

    override suspend fun retryDownload(downloadId: String): RepositoryResult<DownloadRecord> {
        val record = downloadGateway.observeDownloads().first().firstOrNull { it.id == downloadId }
            ?: return RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE),
            )

        return when (val confirmation = confirm(record.paperId)) {
            is RepositoryResult.Failure -> confirmation
            is RepositoryResult.Success -> downloadGateway.retry(downloadId, confirmation.value.descriptor)
        }
    }

    override suspend fun cancelDownload(downloadId: String): RepositoryResult<Unit> = downloadGateway.cancel(downloadId)

    /** Deletion goes through the gateway, which owns both the record and the file it wrote, so the
     * two can never fall out of step. Nothing here touches saved papers — the offline-library
     * capability requires a cleared download to leave the saved paper alone. */
    override suspend fun clearCompletedDownloads(): RepositoryResult<Unit> = downloadGateway.clearCompleted()

    /**
     * Narrows the endpoint's answer to the one case that authorizes a transfer. Every other
     * outcome — no PDF, not found, offline, timeout, upstream unavailable, mismatched identity,
     * malformed body, expired descriptor — returns a failure, so no work is enqueued and the
     * existing record and any completed file are left exactly as they were.
     */
    private suspend fun confirm(paperId: PaperId): RepositoryResult<DownloadAvailability.Available> {
        return when (val result = paperRepository.confirmDownloadAvailability(paperId)) {
            is RepositoryResult.Failure -> result
            is RepositoryResult.Success -> when (val availability = result.value) {
                is DownloadAvailability.Available -> RepositoryResult.Success(availability)
                DownloadAvailability.Unavailable ->
                    RepositoryResult.Failure(RepositoryError.NoDownloadableContent)
            }
        }
    }

    private fun accessNoLongerConfirmed(): RepositoryResult.Failure {
        return RepositoryResult.Failure(RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR))
    }
}
