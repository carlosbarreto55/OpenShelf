package com.example.paperReader.data.local

import com.example.paperReader.domain.model.PaperQuery
import java.util.Locale

/** Deterministic cache key for a normalized query + filters + sort combination, used as the Room
 * primary key for [com.example.paperReader.data.local.entity.SearchQueryEntity]. */
fun PaperQuery.toCacheKey(): String {
    return listOf(
        text.value.trim().lowercase(Locale.US),
        sortOrder.wireValue,
        filters.openAccessOnly.toString(),
        filters.pdfAvailableOnly.toString(),
        filters.publicationYear?.toString().orEmpty(),
        filters.paperType?.name.orEmpty(),
        filters.topic?.trim()?.lowercase(Locale.US).orEmpty(),
    ).joinToString("|")
}
