package com.example.paperReader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaperSearchRequestDto(
    val query: String,
    val cursor: String? = null,
    val sort: String = "relevance",
    val filters: SearchFiltersDto = SearchFiltersDto(),
)

/**
 * Null means "no restriction", which is also what an omitted parameter means. Sending an explicit
 * `false` is not the same thing to every server version (TECHNICAL_DEBT.md API-002), so an inactive
 * restriction is simply absent here and absent on the wire.
 */
@Serializable
data class SearchFiltersDto(
    @SerialName("open_access") val openAccessOnly: Boolean? = null,
    @SerialName("pdf_available") val pdfAvailableOnly: Boolean? = null,
    @SerialName("publication_year") val publicationYear: Int? = null,
    @SerialName("work_type") val workType: String? = null,
    val topic: String? = null,
)

@Serializable
data class PaperDetailsRequestDto(
    @SerialName("paper_id") val paperId: String,
)

@Serializable
data class DownloadDescriptorRequestDto(
    @SerialName("paper_id") val paperId: String,
)

@Serializable
data class PaperSearchResponseDto(
    val items: List<PaperDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("total_count") val totalCount: Int? = null,
)

@Serializable
data class PaperDetailResponseDto(
    val paper: PaperDto? = null,
)

@Serializable
data class DownloadDescriptorResponseDto(
    @SerialName("paper_id") val paperId: String? = null,
    val descriptor: ConfirmedPdfDownloadDescriptorDto? = null,
)

@Serializable
data class PaperDto(
    val id: String? = null,
    val title: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val venue: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val topics: List<TopicDto> = emptyList(),
    @SerialName("citation_count") val citationCount: Int? = null,
    @SerialName("abstract") val abstractText: String? = null,
    val access: AccessResolutionDto? = null,
)

@Serializable
data class AuthorDto(
    val id: String? = null,
    val name: String? = null,
    val orcid: String? = null,
)

@Serializable
data class TopicDto(
    val id: String? = null,
    val name: String? = null,
    val score: Double? = null,
)

@Serializable
data class AccessResolutionDto(
    val state: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    val descriptor: ConfirmedPdfDownloadDescriptorDto? = null,
    val provenance: String? = null,
)

@Serializable
data class ConfirmedPdfDownloadDescriptorDto(
    val url: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val provenance: String? = null,
    val generated: Boolean = false,
    @SerialName("expires_at_epoch_millis") val expiresAtEpochMillis: Long? = null,
)
