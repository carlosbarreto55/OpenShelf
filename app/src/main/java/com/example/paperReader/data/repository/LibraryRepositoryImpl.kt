package com.example.paperReader.data.repository

import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.LocalMappers.toDomain
import com.example.paperReader.data.local.LocalMappers.toEntity
import com.example.paperReader.data.local.LocalMappers.toSnapshotEntity
import com.example.paperReader.data.local.dao.AccessSnapshotDao
import com.example.paperReader.data.local.dao.PaperDao
import com.example.paperReader.data.local.dao.SavedPaperDao
import com.example.paperReader.data.local.entity.SavedPaperEntity
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepositoryImpl @Inject constructor(
    private val savedPaperDao: SavedPaperDao,
    private val paperDao: PaperDao,
    private val accessSnapshotDao: AccessSnapshotDao,
    private val clock: Clock,
) : LibraryRepository {

    override fun observeSavedPapers(): Flow<List<Paper>> {
        return savedPaperDao.observeSaved().map { rows ->
            rows.map { it.toDomain(ACCESS_FRESHNESS_MILLIS, clock.nowEpochMillis()) }
        }
    }

    override suspend fun save(paper: Paper): RepositoryResult<Unit> {
        val now = clock.nowEpochMillis()
        paperDao.upsertAll(listOf(paper.toEntity(now)))
        accessSnapshotDao.upsert(paper.access.toSnapshotEntity(paper.id.value, now))
        savedPaperDao.upsert(SavedPaperEntity(paperId = paper.id.value, savedAtEpochMillis = now))
        return RepositoryResult.Success(Unit)
    }

    override suspend fun unsave(paperId: PaperId): RepositoryResult<Unit> {
        savedPaperDao.delete(paperId.value)
        return RepositoryResult.Success(Unit)
    }
}
