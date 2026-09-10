package com.example.paperReader.data.remote

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
            // `takeIf { it }` is the omission rule: only an enabled restriction is expressed.
            openAccessOnly = query.filters.openAccessOnly.takeIf { it },
            pdfAvailableOnly = query.filters.pdfAvailableOnly.takeIf { it },
            publicationYear = query.filters.publicationYear,
            // Canonical value from the enum, never text derived from its Kotlin name.
            workType = query.filters.paperType?.wireValue,
            topic = query.filters.topic,
        ),
    )
}

fun PaperId.toDetailsRequestDto(): PaperDetailsRequestDto = PaperDetailsRequestDto(value)

fun PaperId.toDownloadDescriptorRequestDto(): DownloadDescriptorRequestDto = DownloadDescriptorRequestDto(value)
