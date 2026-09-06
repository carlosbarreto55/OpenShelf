package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessRefreshRequestDto
import com.example.paperReader.data.remote.dto.DownloadDescriptorRequestDto
import com.example.paperReader.data.remote.dto.PaperDetailsRequestDto
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.dto.SearchFiltersDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperSearchRequest

fun PaperSearchRequest.toDto(): PaperSearchRequestDto {
    return PaperSearchRequestDto(
        query = query.text.value,
        cursor = cursor,
        sort = query.sortOrder.wireValue,
        filters = SearchFiltersDto(
            openAccessOnly = query.filters.openAccessOnly,
            pdfAvailableOnly = query.filters.pdfAvailableOnly,
            publicationYear = query.filters.publicationYear,
            workType = query.filters.paperType?.name?.lowercase(),
            topic = query.filters.topic,
        ),
    )
}

fun PaperId.toDetailsRequestDto(): PaperDetailsRequestDto = PaperDetailsRequestDto(value)

fun PaperId.toAccessRefreshRequestDto(): AccessRefreshRequestDto = AccessRefreshRequestDto(value)

fun PaperId.toDownloadDescriptorRequestDto(): DownloadDescriptorRequestDto = DownloadDescriptorRequestDto(value)
