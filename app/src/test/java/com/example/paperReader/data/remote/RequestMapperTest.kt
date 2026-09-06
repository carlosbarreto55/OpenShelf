package com.example.paperReader.data.remote

import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestMapperTest {
    @Test
    fun searchRequestMapsToNormalizedBackendFields() {
        val dto = PaperSearchRequest(
            query = PaperQuery(
                text = SearchQuery("AI harness"),
                filters = PaperFilters(
                    openAccessOnly = true,
                    pdfAvailableOnly = true,
                    publicationYear = 2024,
                    paperType = PaperType.REVIEW,
                    topic = "education",
                ),
                sortOrder = SortOrder.NEWEST,
            ),
            cursor = "cursor-2",
        ).toDto()

        assertEquals("AI harness", dto.query)
        assertEquals("cursor-2", dto.cursor)
        assertEquals("newest", dto.sort)
        assertEquals(true, dto.filters.openAccessOnly)
        assertEquals(true, dto.filters.pdfAvailableOnly)
        assertEquals(2024, dto.filters.publicationYear)
        assertEquals("review", dto.filters.workType)
        assertEquals("education", dto.filters.topic)
    }
}
