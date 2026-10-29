package com.example.paperReader.domain.usecase

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What Read PDF does (design.md decision 7), shared by search, detail and library so the three
 * cannot disagree. It answers with the download record to open the reader on, and never with a
 * record that does not exist yet: the reader is only entered once there is one.
 *
 * - A completed download whose file is still present opens as it is, with no network call.
 * - A queued or running one is followed, not duplicated.
 * - Anything else — no record, failed, canceled, completed with its file gone — starts a download
 *   through [DownloadsRepository.startDownload], so it is confirmed with the backend first, and
 *   answers only if that succeeds.
 *
 * The paper's current access must still be a confirmed PDF, even to open a file already on the
 * device: this answers an access action, and a local copy is not an access decision.
 */
class ReadPaperUseCase @Inject constructor(
    private val downloadsRepository: DownloadsRepository,
    private val downloadedFiles: DownloadedFiles,
) {
    /** A second tap while the first is still deciding waits for it, then finds its record. */
    private val deciding = Mutex()

    /** Answers the id of the download record to read. */
    suspend operator fun invoke(paper: Paper): RepositoryResult<String> = deciding.withLock {
        if (paper.access !is AccessResolution.DirectPdf) {
            return RepositoryResult.Failure(
                RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR),
            )
        }

        val record = downloadsRepository.observeDownloads().first().firstOrNull { it.paperId == paper.id }
        when (record?.state) {
            DownloadState.QUEUED, DownloadState.RUNNING -> return RepositoryResult.Success(record.id)
            DownloadState.COMPLETED -> {
                val uri = record.localContentUri
                if (uri != null && downloadedFiles.isPresent(uri)) return RepositoryResult.Success(record.id)
            }
            DownloadState.FAILED, DownloadState.CANCELED, null -> Unit
        }

        when (val started = downloadsRepository.startDownload(paper)) {
            is RepositoryResult.Success -> RepositoryResult.Success(started.value.id)
            is RepositoryResult.Failure -> started
        }
    }
}
