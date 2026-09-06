package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeSavedPapers(): Flow<List<Paper>>

    suspend fun save(paper: Paper): RepositoryResult<Unit>

    suspend fun unsave(paperId: PaperId): RepositoryResult<Unit>
}
