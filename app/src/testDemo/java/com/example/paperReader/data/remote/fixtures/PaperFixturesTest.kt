package com.example.paperReader.data.remote.fixtures

import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.repository.RepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperFixturesTest {

    @Test
    fun catalogDecodesFromJsonUsingProductionDtoSchema() {
        assertEquals(7, PaperFixtures.catalog.size)
    }

    @Test
    fun everyFixturePaperMapsThroughRealMappingWithoutRejection() {
        PaperFixtures.catalog.forEach { dto ->
            val result = PaperMappers.mapPaper(dto)
            assertTrue("Expected ${dto.id} to map successfully", result is RepositoryResult.Success)
        }
    }

    @Test
    fun directPdfFixtureMapsToConfirmedDownloadAction() {
        val paper = mappedPaper("W2741809807")
        assertTrue(paper.access is AccessResolution.DirectPdf)
    }

    @Test
    fun openFullTextFixtureMapsToConfirmedReadOnlineAction() {
        val paper = mappedPaper("W3123456789")
        assertTrue(paper.access is AccessResolution.OpenFullText)
    }

    @Test
    fun noFreeFullTextFixtureMapsToNoFreeFullText() {
        val paper = mappedPaper("W3987654321")
        assertEquals(AccessResolution.NoFreeFullText, paper.access)
    }

    @Test
    fun paywalledFixtureMapsToPaywalled() {
        val paper = mappedPaper("W4014455667")
        assertEquals(AccessResolution.Paywalled, paper.access)
    }

    @Test
    fun unrecognizedFutureAccessStateFailsClosedToUnknown() {
        val paper = mappedPaper("W4022233445")
        assertEquals(AccessResolution.Unknown, paper.access)
    }

    @Test
    fun paperMissingEveryOptionalFieldStillMapsWithNoAccessAffordance() {
        val paper = mappedPaper("W4031122556")
        assertEquals(AccessResolution.Unknown, paper.access)
        assertEquals(null, paper.venue)
        assertEquals(null, paper.publicationYear)
        assertEquals(null, paper.citationCount)
        assertTrue(paper.topics.isEmpty())
    }

    @Test
    fun emptySearchResponseFixtureDecodesWithNoItemsOrCursor() {
        val response = PaperFixtures.emptySearchResponse
        assertTrue(response.items.isEmpty())
        assertEquals(null, response.nextCursor)
    }

    private fun mappedPaper(id: String) = PaperFixtures.catalog
        .first { it.id == id }
        .let { PaperMappers.mapPaper(it) as RepositoryResult.Success }
        .value
}
