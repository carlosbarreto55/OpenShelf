package com.example.paperReader.data.remote

import com.example.paperReader.data.local.toCacheKey
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun inactiveRestrictionsAreAbsentRatherThanExplicitlyFalse() {
        val dto = PaperSearchRequest(PaperQuery(text = SearchQuery("AI"))).toDto()

        // `false` and "omitted" are the same thing to the contract, but an older server read
        // `open_access=false` as a restriction (API-002). Absence is the only safe encoding.
        assertNull(dto.filters.openAccessOnly)
        assertNull(dto.filters.pdfAvailableOnly)
        assertNull(dto.filters.publicationYear)
        assertNull(dto.filters.workType)
        assertNull(dto.filters.topic)
    }

    @Test
    fun eachRestrictionIsEncodedIndependently() {
        val openAccessOnly = PaperSearchRequest(
            PaperQuery(text = SearchQuery("AI"), filters = PaperFilters(openAccessOnly = true)),
        ).toDto()

        assertEquals(true, openAccessOnly.filters.openAccessOnly)
        assertNull(openAccessOnly.filters.pdfAvailableOnly)
    }

    @Test
    fun workTypeIsEmittedAsItsCanonicalContractValue() {
        val dto = PaperSearchRequest(
            PaperQuery(text = SearchQuery("history"), filters = PaperFilters(paperType = PaperType.BOOK_CHAPTER)),
        ).toDto()

        assertEquals("book_chapter", dto.filters.workType)
    }

    @Test
    fun typesWithNoCanonicalValueAreNotSentAtAll() {
        listOf(PaperType.OTHER, PaperType.UNKNOWN).forEach { type ->
            val dto = PaperSearchRequest(
                PaperQuery(text = SearchQuery("AI"), filters = PaperFilters(paperType = type)),
            ).toDto()

            assertNull("$type must never reach the wire", dto.filters.workType)
        }
    }

    @Test
    fun topicStaysFreeTextAndIsNeverTurnedIntoAnIdentifier() {
        val dto = PaperSearchRequest(
            PaperQuery(text = SearchQuery("learning"), filters = PaperFilters(topic = "education")),
        ).toDto()

        assertEquals("education", dto.filters.topic)
    }

    @Test
    fun normalizedCacheKeysDistinguishYearAndTypeCombinations() {
        val base = PaperQuery(text = SearchQuery("AI"))
        val keys = listOf(
            base,
            base.copy(filters = PaperFilters(publicationYear = 2023)),
            base.copy(filters = PaperFilters(publicationYear = 2024)),
            base.copy(filters = PaperFilters(paperType = PaperType.REVIEW)),
            base.copy(filters = PaperFilters(publicationYear = 2024, paperType = PaperType.REVIEW)),
        ).map { it.toCacheKey() }

        assertEquals("Each combination must cache separately", keys.size, keys.toSet().size)
    }
}
