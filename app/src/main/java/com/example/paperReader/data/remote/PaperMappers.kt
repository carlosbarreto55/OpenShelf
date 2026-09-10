package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessResolutionDto
import com.example.paperReader.data.remote.dto.ConfirmedPdfDownloadDescriptorDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Author
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PublicationYear
import com.example.paperReader.domain.model.Topic
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import java.net.URI
import java.util.Locale

object PaperMappers {
    fun mapPaper(dto: PaperDto): RepositoryResult<Paper> {
        val id = PaperId.from(dto.id)
            ?: return RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
            )
        val title = dto.title?.trim()?.takeIf(String::isNotBlank)
            ?: return RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.MISSING_TITLE),
            )

        val access = when (val accessResult = mapAccessResolution(dto.access)) {
            is RepositoryResult.Success -> accessResult.value
            is RepositoryResult.Failure -> AccessResolution.Unknown
        }

        return RepositoryResult.Success(
            Paper(
                id = id,
                title = title,
                authors = dto.authors.mapNotNull { it.toDomain() },
                venue = dto.venue.cleanOptional(),
                publicationYear = dto.year?.takeIf { it in PublicationYear.SUPPORTED_RANGE },
                type = com.example.paperReader.domain.model.PaperType.fromWireValue(dto.type),
                topics = dto.topics.mapNotNull { it.toDomain() },
                citationCount = dto.citationCount?.takeIf { it >= 0 },
                abstractText = dto.abstractText.cleanOptional(),
                access = access,
            ),
        )
    }

    fun mapSearchResponse(dto: PaperSearchResponseDto): RepositoryResult<CursorPage<Paper>> {
        val papers = dto.items.mapNotNull { item ->
            when (val result = mapPaper(item)) {
                is RepositoryResult.Success -> result.value
                is RepositoryResult.Failure -> null
            }
        }
        if (dto.items.isNotEmpty() && papers.isEmpty()) {
            return RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.ALL_ITEMS_INVALID),
            )
        }
        return RepositoryResult.Success(
            CursorPage(
                items = papers,
                nextCursor = dto.nextCursor.cleanOptional(),
                // Carried through exactly as supplied. A negative total is not a total; absence
                // stays absence. Nothing here is ever derived from `papers.size`.
                totalItems = dto.totalCount?.takeIf { it >= 0 },
            ),
        )
    }

    /**
     * A 200 response that carries no usable paper is malformed data, never "not found" — the
     * backend signals a missing paper with HTTP 404 (business decision 3). [expectedPaperId] is
     * checked so a response about a different work cannot be shown as the requested one.
     */
    fun mapDetailResponse(
        dto: PaperDetailResponseDto,
        expectedPaperId: PaperId,
    ): RepositoryResult<Paper> {
        val paper = dto.paper
            ?: return RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE),
            )
        return when (val mapped = mapPaper(paper)) {
            is RepositoryResult.Failure -> mapped
            is RepositoryResult.Success -> if (mapped.value.id == expectedPaperId) {
                mapped
            } else {
                RepositoryResult.Failure(
                    RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
                )
            }
        }
    }

    /**
     * The four outcomes the download endpoint can produce on a 200 response, kept distinct:
     * a valid descriptor, the normal no-PDF case, a response about the wrong paper, and a
     * descriptor that fails validation (malformed URL, or expired against [nowEpochMillis]).
     * HTTP 404 never reaches here — it is already [RepositoryError.NotFound].
     */
    fun mapDownloadAvailability(
        dto: DownloadDescriptorResponseDto,
        expectedPaperId: PaperId,
        nowEpochMillis: Long,
    ): RepositoryResult<DownloadAvailability> {
        val paperIdFailure = validateResponsePaperId(dto.paperId, expectedPaperId)
        if (paperIdFailure != null) {
            return RepositoryResult.Failure(paperIdFailure)
        }
        val descriptorDto = dto.descriptor
            ?: return RepositoryResult.Success(DownloadAvailability.Unavailable)

        return when (val mapped = mapPdfDescriptor(descriptorDto)) {
            is RepositoryResult.Failure -> mapped
            is RepositoryResult.Success -> {
                val expiresAt = mapped.value.expiresAtEpochMillis
                if (expiresAt != null && expiresAt <= nowEpochMillis) {
                    RepositoryResult.Failure(
                        RepositoryError.AccessValidation(RepositoryError.AccessFailure.EXPIRED_DESCRIPTOR),
                    )
                } else {
                    RepositoryResult.Success(DownloadAvailability.Available(mapped.value))
                }
            }
        }
    }

    fun mapAccessResolution(dto: AccessResolutionDto?): RepositoryResult<AccessResolution> {
        if (dto == null) return RepositoryResult.Success(AccessResolution.Unknown)

        return when (dto.state?.trim()?.uppercase(Locale.US)) {
            "DIRECT_PDF" -> dto.descriptor?.let(::mapPdfDescriptor)?.let { result ->
                when (result) {
                    is RepositoryResult.Success -> RepositoryResult.Success(
                        AccessResolution.DirectPdf(result.value),
                    )
                    is RepositoryResult.Failure -> result
                }
            } ?: RepositoryResult.Failure(
                RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR),
            )

            "OPEN_FULL_TEXT" -> {
                val sourceUrl = dto.sourceUrl.cleanOptional()
                if (sourceUrl != null && sourceUrl.isHttpsUrl()) {
                    RepositoryResult.Success(
                        AccessResolution.OpenFullText(
                            sourceUrl = sourceUrl,
                            provenance = dto.provenance.cleanOptional(),
                        ),
                    )
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_SOURCE_URL),
                    )
                }
            }

            "NO_FREE_FULL_TEXT" -> RepositoryResult.Success(AccessResolution.NoFreeFullText)
            "PAYWALLED" -> RepositoryResult.Success(AccessResolution.Paywalled)
            "CHECKING" -> RepositoryResult.Success(AccessResolution.Checking)
            "ERROR" -> RepositoryResult.Success(AccessResolution.Error)
            else -> RepositoryResult.Success(AccessResolution.Unknown)
        }
    }

    private fun mapPdfDescriptor(dto: ConfirmedPdfDownloadDescriptorDto): RepositoryResult<ConfirmedPdfDescriptor> {
        val url = dto.url.cleanOptional()
        if (url == null || !url.isHttpsUrl() || dto.expiresAtEpochMillis?.let { it <= 0 } == true) {
            return RepositoryResult.Failure(
                RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR),
            )
        }
        return RepositoryResult.Success(
            ConfirmedPdfDescriptor(
                url = url,
                mimeType = dto.mimeType.cleanOptional(),
                provenance = dto.provenance.cleanOptional(),
                generated = dto.generated,
                expiresAtEpochMillis = dto.expiresAtEpochMillis,
            ),
        )
    }

    private fun validateResponsePaperId(
        rawPaperId: String?,
        expectedPaperId: PaperId,
    ): RepositoryError? {
        val actualPaperId = PaperId.from(rawPaperId)
        return if (actualPaperId == expectedPaperId) {
            null
        } else {
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID)
        }
    }

    private fun String?.cleanOptional(): String? = this?.trim()?.takeIf(String::isNotBlank)

    private fun com.example.paperReader.data.remote.dto.AuthorDto.toDomain(): Author? {
        val name = name.cleanOptional() ?: return null
        return Author(name = name, id = id.cleanOptional(), orcid = orcid.cleanOptional())
    }

    private fun com.example.paperReader.data.remote.dto.TopicDto.toDomain(): Topic? {
        val name = name.cleanOptional() ?: return null
        return Topic(name = name, id = id.cleanOptional(), score = score?.takeIf { it >= 0 })
    }

    private fun String.isHttpsUrl(): Boolean {
        return runCatching {
            val uri = URI(this)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }
}
