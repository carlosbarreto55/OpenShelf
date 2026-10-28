package com.example.paperReader.ui.search

import androidx.annotation.StringRes
import com.example.paperReader.R
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperType

/** Stable identity for one filter, so a surface can ask "is this one on?" without re-deriving the
 * answer from [PaperFilters] itself. */
enum class ActiveFilterId {
    OPEN_ACCESS,
    PDF_AVAILABLE,
    PUBLICATION_YEAR,
    PAPER_TYPE,
    TOPIC,
}

/**
 * One filter that is currently narrowing the search: what to call it, and the filters that would
 * remain if just this one were removed.
 *
 * [clear] returns a new [PaperFilters] rather than mutating anything, so the empty state and the
 * chip row both remove a filter through exactly the same path a chip tap takes.
 */
class ActiveFilter(
    val id: ActiveFilterId,
    @param:StringRes val labelRes: Int,
    val labelArgs: List<Any>,
    val clear: () -> PaperFilters,
)

/**
 * The one place that enumerates which filters are active (design.md decision 4). The chip row and
 * the empty state both read this, so adding a filter means adding a branch here and nothing else —
 * rather than a field-by-field comparison against `PaperFilters()` repeated at every call site.
 */
fun activeFiltersOf(filters: PaperFilters): List<ActiveFilter> = buildList {
    if (filters.openAccessOnly) {
        add(
            ActiveFilter(
                id = ActiveFilterId.OPEN_ACCESS,
                labelRes = R.string.filter_open_access,
                labelArgs = emptyList(),
                clear = { filters.copy(openAccessOnly = false) },
            ),
        )
    }
    if (filters.pdfAvailableOnly) {
        add(
            ActiveFilter(
                id = ActiveFilterId.PDF_AVAILABLE,
                labelRes = R.string.filter_pdf_available,
                labelArgs = emptyList(),
                clear = { filters.copy(pdfAvailableOnly = false) },
            ),
        )
    }
    filters.publicationYear?.let { year ->
        add(
            ActiveFilter(
                id = ActiveFilterId.PUBLICATION_YEAR,
                labelRes = R.string.filter_year_active,
                labelArgs = listOf(year),
                clear = { filters.copy(publicationYear = null) },
            ),
        )
    }
    // `OTHER` and `UNKNOWN` are parse outcomes, never filter values, so a filter carrying one is
    // not something the user chose and is not presented as removable.
    filters.paperType?.filterLabelRes()?.let { labelRes ->
        add(
            ActiveFilter(
                id = ActiveFilterId.PAPER_TYPE,
                labelRes = labelRes,
                labelArgs = emptyList(),
                clear = { filters.copy(paperType = null) },
            ),
        )
    }
    filters.topic?.takeIf { it.isNotBlank() }?.let { topic ->
        add(
            ActiveFilter(
                id = ActiveFilterId.TOPIC,
                labelRes = R.string.filter_topic_active,
                labelArgs = listOf(topic),
                clear = { filters.copy(topic = null) },
            ),
        )
    }
}

/** The label for a work type the contract actually publishes as a filter value; null for the two
 * parse-only values. */
@StringRes
fun PaperType.filterLabelRes(): Int? = when (this) {
    PaperType.ARTICLE -> R.string.filter_type_article
    PaperType.REVIEW -> R.string.filter_type_review
    PaperType.PREPRINT -> R.string.filter_type_preprint
    PaperType.BOOK_CHAPTER -> R.string.filter_type_book_chapter
    PaperType.DATASET -> R.string.filter_type_dataset
    PaperType.OTHER, PaperType.UNKNOWN -> null
}
