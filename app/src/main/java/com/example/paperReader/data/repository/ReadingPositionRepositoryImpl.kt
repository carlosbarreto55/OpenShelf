package com.example.paperReader.data.repository

import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.dao.ReadingPositionDao
import com.example.paperReader.data.local.entity.ReadingPositionEntity
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.model.ResumablePaper
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.repository.ReadingPositionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingPositionRepositoryImpl @Inject constructor(
    private val dao: ReadingPositionDao,
    private val downloadedFiles: DownloadedFiles,
    private val clock: Clock,
) : ReadingPositionRepository {

    override suspend fun position(paperId: PaperId): ReadingPosition? {
        // A row from an older build, or one written mid-change, is a reason to start at the top
        // rather than to crash on open.
        return dao.get(paperId.value)?.let { row ->
            runCatching { ReadingPosition(row.pageIndex, row.pageOffsetFraction) }.getOrNull()
        }
    }

    override suspend fun save(paperId: PaperId, position: ReadingPosition) {
        dao.upsert(
            ReadingPositionEntity(
                paperId = paperId.value,
                pageIndex = position.pageIndex,
                pageOffsetFraction = position.pageOffsetFraction,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    /** The query already restricts this to completed downloads that name a file; whether the file
     * is really there is checked as well, because the removal cascade and this check each miss a
     * case the other catches (design.md, "A stored position outlives its file"). */
    override fun observeResumable(): Flow<List<ResumablePaper>> = dao.observeResumable().map { rows ->
        rows.filter { downloadedFiles.isPresent(it.localContentUri) }.mapNotNull { row ->
            val position = runCatching { ReadingPosition(row.pageIndex, row.pageOffsetFraction) }.getOrNull()
                ?: return@mapNotNull null
            ResumablePaper(
                paperId = PaperId(row.paperId),
                downloadId = row.downloadId,
                title = row.title,
                position = position,
                lastReadAtEpochMillis = row.updatedAtEpochMillis,
            )
        }
    }
}
