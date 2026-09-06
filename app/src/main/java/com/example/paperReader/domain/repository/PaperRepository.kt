package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import kotlinx.coroutines.flow.Flow

interface PaperRepository {
    fun observeSearch(query: PaperQuery): Flow<CursorPage<Paper>>

    suspend fun search(request: PaperSearchRequest): RepositoryResult<CursorPage<Paper>>

    fun observePaper(paperId: PaperId): Flow<Paper?>

    suspend fun refreshPaper(paperId: PaperId): RepositoryResult<Paper>

    suspend fun refreshAccess(paperId: PaperId): RepositoryResult<AccessResolution>
}
