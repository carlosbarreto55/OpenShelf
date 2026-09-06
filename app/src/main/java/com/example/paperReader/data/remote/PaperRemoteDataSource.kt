package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessRefreshResponseDto
import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryResult

interface PaperRemoteDataSource {
    suspend fun search(request: PaperSearchRequestDto): RepositoryResult<PaperSearchResponseDto>

    suspend fun getPaper(paperId: PaperId): RepositoryResult<PaperDetailResponseDto>

    suspend fun refreshAccess(paperId: PaperId): RepositoryResult<AccessRefreshResponseDto>

    suspend fun getDownloadDescriptor(paperId: PaperId): RepositoryResult<DownloadDescriptorResponseDto>
}
