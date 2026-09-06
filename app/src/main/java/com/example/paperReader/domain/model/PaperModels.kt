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

enum class PaperType {
    ARTICLE,
    REVIEW,
    PREPRINT,
    BOOK_CHAPTER,
    DATASET,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromWireValue(value: String?): PaperType {
            return when (value?.trim()?.uppercase(Locale.US)) {
                "ARTICLE", "JOURNAL_ARTICLE" -> ARTICLE
                "REVIEW" -> REVIEW
                "PREPRINT" -> PREPRINT
                "BOOK_CHAPTER" -> BOOK_CHAPTER
                "DATASET" -> DATASET
                "", null -> UNKNOWN
                else -> OTHER
            }
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

data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
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
    val errorMessage: String? = null,
    val updatedAtEpochMillis: Long,
)
