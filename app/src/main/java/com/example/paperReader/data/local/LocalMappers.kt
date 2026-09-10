package com.example.paperReader.data.local

import com.example.paperReader.data.local.entity.AccessSnapshotEntity
import com.example.paperReader.data.local.entity.PaperEntity
import com.example.paperReader.data.local.entity.PaperRow
import com.example.paperReader.data.local.entity.PersistedAuthor
import com.example.paperReader.data.local.entity.PersistedTopic
import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.dto.AccessResolutionDto
import com.example.paperReader.data.remote.dto.ConfirmedPdfDownloadDescriptorDto
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Author
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.model.Topic
import com.example.paperReader.domain.repository.RepositoryResult

object LocalMappers {
    fun Paper.toEntity(updatedAtEpochMillis: Long): PaperEntity {
        return PaperEntity(
            id = id.value,
            title = title,
            authors = authors.map { PersistedAuthor(name = it.name, id = it.id, orcid = it.orcid) },
            venue = venue,
            publicationYear = publicationYear,
            type = type,
            topics = topics.map { PersistedTopic(name = it.name, id = it.id, score = it.score) },
            citationCount = citationCount,
            abstractText = abstractText,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    fun AccessResolution.toSnapshotEntity(paperId: String, checkedAtEpochMillis: Long): AccessSnapshotEntity {
        return when (this) {
            is AccessResolution.DirectPdf -> AccessSnapshotEntity(
                paperId = paperId,
                state = "DIRECT_PDF",
                sourceUrl = null,
                descriptorUrl = descriptor.url,
                descriptorMimeType = descriptor.mimeType,
                descriptorProvenance = descriptor.provenance,
                descriptorGenerated = descriptor.generated,
                descriptorExpiresAtEpochMillis = descriptor.expiresAtEpochMillis,
                provenance = null,
                checkedAtEpochMillis = checkedAtEpochMillis,
            )

            is AccessResolution.OpenFullText -> AccessSnapshotEntity(
                paperId = paperId,
                state = "OPEN_FULL_TEXT",
                sourceUrl = sourceUrl,
                descriptorUrl = null,
                descriptorMimeType = null,
                descriptorProvenance = null,
                descriptorGenerated = false,
                descriptorExpiresAtEpochMillis = null,
                provenance = provenance,
                checkedAtEpochMillis = checkedAtEpochMillis,
            )

            AccessResolution.NoFreeFullText -> emptySnapshot(paperId, "NO_FREE_FULL_TEXT", checkedAtEpochMillis)
            AccessResolution.Paywalled -> emptySnapshot(paperId, "PAYWALLED", checkedAtEpochMillis)
            AccessResolution.Checking -> emptySnapshot(paperId, "CHECKING", checkedAtEpochMillis)
            AccessResolution.Error -> emptySnapshot(paperId, "ERROR", checkedAtEpochMillis)
            AccessResolution.Unknown -> emptySnapshot(paperId, "UNKNOWN", checkedAtEpochMillis)
        }
    }

    private fun emptySnapshot(paperId: String, state: String, checkedAtEpochMillis: Long) = AccessSnapshotEntity(
        paperId = paperId,
        state = state,
        sourceUrl = null,
        descriptorUrl = null,
        descriptorMimeType = null,
        descriptorProvenance = null,
        descriptorGenerated = false,
        descriptorExpiresAtEpochMillis = null,
        provenance = null,
        checkedAtEpochMillis = checkedAtEpochMillis,
    )

    /** Rebuilds the domain [Paper], re-validating the persisted access snapshot through the same
     * fail-closed [PaperMappers.mapAccessResolution] logic a fresh remote response would use, and
     * downgrading an actionable state to [AccessResolution.Unknown] once it is older than
     * [accessFreshnessMillis] rather than assuming it is still true. */
    fun PaperRow.toDomain(accessFreshnessMillis: Long, nowEpochMillis: Long): Paper {
        return Paper(
            id = PaperId(id),
            title = title,
            authors = authors.map { Author(name = it.name, id = it.id, orcid = it.orcid) },
            venue = venue,
            publicationYear = publicationYear,
            type = type,
            topics = topics.map { Topic(name = it.name, id = it.id, score = it.score) },
            citationCount = citationCount,
            abstractText = abstractText,
            access = toAccessResolution(accessFreshnessMillis, nowEpochMillis),
            savedState = if (isSaved) SavedState.SAVED else SavedState.NOT_SAVED,
        )
    }

    private fun PaperRow.toAccessResolution(accessFreshnessMillis: Long, nowEpochMillis: Long): AccessResolution {
        val state = accessState ?: return AccessResolution.Unknown
        val isActionable = state == "DIRECT_PDF" || state == "OPEN_FULL_TEXT"
        if (isActionable) {
            val checkedAt = accessCheckedAtEpochMillis ?: return AccessResolution.Unknown
            if (nowEpochMillis - checkedAt > accessFreshnessMillis) return AccessResolution.Unknown
        }
        val dto = AccessResolutionDto(
            state = state,
            sourceUrl = accessSourceUrl,
            descriptor = accessDescriptorUrl?.let {
                ConfirmedPdfDownloadDescriptorDto(
                    url = it,
                    mimeType = accessDescriptorMimeType,
                    provenance = accessDescriptorProvenance,
                    generated = accessDescriptorGenerated ?: false,
                    expiresAtEpochMillis = accessDescriptorExpiresAtEpochMillis,
                )
            },
            provenance = accessProvenance,
        )
        return when (val result = PaperMappers.mapAccessResolution(dto)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> AccessResolution.Unknown
        }
    }
}
