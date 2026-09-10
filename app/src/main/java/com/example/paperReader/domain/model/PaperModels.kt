package com.example.paperReader.domain.model

import java.util.Locale

@JvmInline
value class PaperId(val value: String) {
    companion object {
        private val stableWorkId = Regex("^W[0-9]+$")

        fun from(raw: String?): PaperId? {
            val value = raw?.trim().orEmpty()
            return value.takeIf(stableWorkId::matches)?.let(::PaperId)
        }
    }
}

@JvmInline
value class SearchQuery(val value: String) {
    companion object {
        fun normalize(raw: String?): SearchQuery? {
            val value = raw
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                .orEmpty()
            return value.takeIf(String::isNotBlank)?.let(::SearchQuery)
        }
    }
}

data class Author(
    val name: String,
    val id: String? = null,
    val orcid: String? = null,
)

data class Topic(
    val name: String,
    val id: String? = null,
    val score: Double? = null,
)

/**
 * [wireValue] is the one canonical value this client ever *emits*. Parsing is deliberately more
 * tolerant than emission: a server mid-rollout can still send a documented legacy alias, and a
 * value we do not recognize must not lose the paper (see TECHNICAL_DEBT.md AND-008 / API-006).
 */
enum class PaperType(val wireValue: String?) {
    ARTICLE("article"),
    REVIEW("review"),
    PREPRINT("preprint"),
    BOOK_CHAPTER("book_chapter"),
    DATASET("dataset"),

    /** A recognized response that is not one of the supported filter values. Never emitted. */
    OTHER(null),

    /** No type was supplied at all. Never emitted. */
    UNKNOWN(null),
    ;

    companion object {
        /** The finite set the public contract publishes, and therefore the only values a filter
         * control may offer. */
        val SUPPORTED_FILTER_TYPES = listOf(ARTICLE, REVIEW, PREPRINT, BOOK_CHAPTER, DATASET)

        /** Documented legacy spellings accepted on input only. `book-chapter` is raw OpenAlex
         * output that an un-migrated server can still emit; `journal_article` predates the
         * canonical `article`. */
        private val LEGACY_ALIASES = mapOf(
            "book_chapter" to BOOK_CHAPTER,
            "journal_article" to ARTICLE,
        )

        fun fromWireValue(value: String?): PaperType {
            // Hyphen and underscore are the same separator for parsing purposes, so `book-chapter`,
            // `BOOK_CHAPTER` and `Book-Chapter` all resolve; emission stays canonical either way.
            val normalized = value?.trim()?.lowercase(Locale.US)?.replace('-', '_').orEmpty()
            if (normalized.isEmpty()) return UNKNOWN
            return SUPPORTED_FILTER_TYPES.firstOrNull { it.wireValue == normalized }
                ?: LEGACY_ALIASES[normalized]
                ?: OTHER
        }
    }
}

enum class SortOrder(val wireValue: String) {
    RELEVANCE("relevance"),
    NEWEST("newest"),
    CITATIONS("citations"),
}

data class PaperFilters(
    val openAccessOnly: Boolean = false,
    val pdfAvailableOnly: Boolean = false,
    val publicationYear: Int? = null,
    val paperType: PaperType? = null,
    val topic: String? = null,
)

data class PaperQuery(
    val text: SearchQuery,
    val filters: PaperFilters = PaperFilters(),
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
)

data class PaperSearchRequest(
    val query: PaperQuery,
    val cursor: String? = null,
)

/**
 * [totalItems] is the matched total the data source supplied, or null when it supplied none. It is
 * never derived from [items], page size, or page count — see TECHNICAL_DEBT.md AND-007.
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val totalItems: Int? = null,
)

enum class SavedState {
    SAVED,
    NOT_SAVED,
}

data class ConfirmedPdfDescriptor(
    val url: String,
    val mimeType: String? = null,
    val provenance: String? = null,
    val generated: Boolean = false,
    val expiresAtEpochMillis: Long? = null,
)

/**
 * The download endpoint's answer for one paper. [Unavailable] is the normal "this paper exists but
 * currently has no confirmed PDF" case (HTTP 200 with a null descriptor) and must never be
 * confused with a malformed body or a missing paper — see TECHNICAL_DEBT.md AND-006.
 */
sealed interface DownloadAvailability {
    data class Available(val descriptor: ConfirmedPdfDescriptor) : DownloadAvailability

    data object Unavailable : DownloadAvailability
}

sealed interface AccessResolution {
    data class DirectPdf(val descriptor: ConfirmedPdfDescriptor) : AccessResolution

    data class OpenFullText(
        val sourceUrl: String,
        val provenance: String? = null,
    ) : AccessResolution

    data object NoFreeFullText : AccessResolution

    data object Paywalled : AccessResolution

    data object Unknown : AccessResolution

    data object Checking : AccessResolution

    data object Error : AccessResolution
}

data class Paper(
    val id: PaperId,
    val title: String,
    val authors: List<Author> = emptyList(),
    val venue: String? = null,
    val publicationYear: Int? = null,
    val type: PaperType = PaperType.UNKNOWN,
    val topics: List<Topic> = emptyList(),
    val citationCount: Int? = null,
    val abstractText: String? = null,
    val access: AccessResolution = AccessResolution.Unknown,
    val savedState: SavedState = SavedState.NOT_SAVED,
)

enum class DownloadState {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
}

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long? = null,
)

data class DownloadRecord(
    val id: String,
    val paperId: PaperId,
    val title: String,
    val state: DownloadState,
    val progress: DownloadProgress? = null,
    val localContentUri: String? = null,
    /** The completed file's actual MIME type (set when [state] becomes [DownloadState.COMPLETED]),
     * not necessarily the confirmed descriptor's declared type — the placeholder implementation's
     * "completed" file is a disclaimer text file, never a PDF. Meaningless before completion. */
    val mimeType: String? = null,
    val errorMessage: String? = null,
    val updatedAtEpochMillis: Long,
)
