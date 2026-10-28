package com.example.paperReader.ui.search

import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single enumeration both the chip row and the empty state read. Its contract is narrow and
 * worth pinning: it names only filters that are actually on, and removing one leaves the rest.
 */
class ActiveFiltersTest {

    @Test
    fun noFiltersMeansNothingToName() {
        assertTrue(activeFiltersOf(PaperFilters()).isEmpty())
    }

    @Test
    fun everyFilterTheUserCanSetIsEnumerated() {
        val all = PaperFilters(
            openAccessOnly = true,
            pdfAvailableOnly = true,
            publicationYear = 2021,
            paperType = PaperType.REVIEW,
            topic = "alignment",
        )

        assertEquals(
            listOf(
                ActiveFilterId.OPEN_ACCESS,
                ActiveFilterId.PDF_AVAILABLE,
                ActiveFilterId.PUBLICATION_YEAR,
                ActiveFilterId.PAPER_TYPE,
                ActiveFilterId.TOPIC,
            ),
            activeFiltersOf(all).map { it.id },
        )
    }

    @Test
    fun clearingOneFilterLeavesTheOthersExactlyAsTheyWere() {
        val filters = PaperFilters(openAccessOnly = true, publicationYear = 2021, paperType = PaperType.ARTICLE)

        val cleared = activeFiltersOf(filters)
            .single { it.id == ActiveFilterId.PUBLICATION_YEAR }
            .clear()

        assertEquals(
            PaperFilters(openAccessOnly = true, publicationYear = null, paperType = PaperType.ARTICLE),
            cleared,
        )
    }

    @Test
    fun clearingEveryFilterInTurnArrivesAtNoFilters() {
        var filters = PaperFilters(
            openAccessOnly = true,
            pdfAvailableOnly = true,
            publicationYear = 2021,
            paperType = PaperType.DATASET,
            topic = "alignment",
        )

        while (activeFiltersOf(filters).isNotEmpty()) {
            filters = activeFiltersOf(filters).first().clear()
        }

        assertEquals(PaperFilters(), filters)
    }

    @Test
    fun aBlankTopicIsNotAFilter() {
        assertTrue(activeFiltersOf(PaperFilters(topic = "   ")).isEmpty())
    }

    @Test
    fun aParseOnlyWorkTypeIsNotPresentedAsARemovableFilter() {
        // OTHER and UNKNOWN are how an unrecognized response value is carried, never something the
        // user chose, so naming them as active filters would be inventing a choice.
        assertTrue(activeFiltersOf(PaperFilters(paperType = PaperType.OTHER)).isEmpty())
        assertTrue(activeFiltersOf(PaperFilters(paperType = PaperType.UNKNOWN)).isEmpty())
    }
}
