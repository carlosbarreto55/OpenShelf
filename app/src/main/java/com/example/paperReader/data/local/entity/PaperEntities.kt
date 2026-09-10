package com.example.paperReader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperType
import kotlinx.serialization.Serializable

@Serializable
data class PersistedAuthor(
    val name: String,
    val id: String? = null,
    val orcid: String? = null,
)

@Serializable
data class PersistedTopic(
    val name: String,
    val id: String? = null,
    val score: Double? = null,
)

@Entity(tableName = "papers")
data class PaperEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: List<PersistedAuthor>,
    val venue: String?,
    val publicationYear: Int?,
    val type: PaperType,
    val topics: List<PersistedTopic>,
    val citationCount: Int?,
    val abstractText: String?,
    val updatedAtEpochMillis: Long,
)

/** Latest normalized access resolution for a paper, kept in the same wire shape as
 * [com.example.paperReader.data.remote.dto.AccessResolutionDto] so it can be re-validated through
 * [com.example.paperReader.data.remote.PaperMappers] on read instead of trusting stored data blindly. */
@Entity(
    tableName = "access_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AccessSnapshotEntity(
    @PrimaryKey val paperId: String,
    val state: String?,
    val sourceUrl: String?,
    val descriptorUrl: String?,
    val descriptorMimeType: String?,
    val descriptorProvenance: String?,
    val descriptorGenerated: Boolean,
    val descriptorExpiresAtEpochMillis: Long?,
    val provenance: String?,
    val checkedAtEpochMillis: Long,
)

/** One row per distinct normalized query (text + filters + sort). Tracks pagination cursor and
 * cache freshness independently from the papers it currently points at. */
@Entity(tableName = "search_queries")
data class SearchQueryEntity(
    @PrimaryKey val queryKey: String,
    val queryText: String,
    val sortOrder: String,
    val openAccessOnly: Boolean,
    val pdfAvailableOnly: Boolean,
    val publicationYear: Int?,
    val workType: String?,
    val topic: String?,
    val nextCursor: String?,
    val totalCount: Int?,
    val fetchedAtEpochMillis: Long,
)

/**
 * Ordered cross-reference between a cached query and the papers it returned, preserving backend
 * order via [position]. Deleting the owning [SearchQueryEntity] cascades here automatically.
 *
 * The unique `(queryKey, paperId)` index is the identity rule: for one normalized query a paper
 * appears at most once, however many overlapping cursor pages contained it. Without it the same
 * paper could occupy several positions, which is both a visible duplicate and a duplicate Compose
 * item key (AND-009).
 */
@Entity(
    tableName = "search_results",
    primaryKeys = ["queryKey", "position"],
    foreignKeys = [
        ForeignKey(
            entity = SearchQueryEntity::class,
            parentColumns = ["queryKey"],
            childColumns = ["queryKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("paperId"),
        Index("queryKey"),
        Index(value = ["queryKey", "paperId"], unique = true),
    ],
)
data class SearchResultEntity(
    val queryKey: String,
    val position: Int,
    val paperId: String,
)

/** Bounded, deduplicated history of submitted query text, independent of the result cache above. */
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val queryText: String,
    val lastUsedAtEpochMillis: Long,
)

/** Presence of a row means the paper is saved; user-owned, never subject to cache pruning. */
@Entity(tableName = "saved_papers")
data class SavedPaperEntity(
    @PrimaryKey val paperId: String,
    val savedAtEpochMillis: Long,
)

/** [sourceUrl]/[mimeType] persist the confirmed descriptor a download was started from, so a
 * retry can requeue the transfer without re-fetching the paper; [DownloadsRepository] still
 * re-checks the paper's *current* access before allowing a retry, this is just the transport
 * detail the gateway itself needs. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val paperId: String,
    val title: String,
    val state: DownloadState,
    val sourceUrl: String,
    val mimeType: String?,
    val bytesDownloaded: Long?,
    val totalBytes: Long?,
    val localContentUri: String?,
    val errorMessage: String?,
    val updatedAtEpochMillis: Long,
)

/** Flat projection joining a paper with its latest access snapshot and saved-state flag. Used for
 * every read path (search results, paper detail, library) instead of separate relation objects so
 * the raw SQL stays in one place per query. */
data class PaperRow(
    val id: String,
    val title: String,
    val authors: List<PersistedAuthor>,
    val venue: String?,
    val publicationYear: Int?,
    val type: PaperType,
    val topics: List<PersistedTopic>,
    val citationCount: Int?,
    val abstractText: String?,
    val accessState: String?,
    val accessSourceUrl: String?,
    val accessDescriptorUrl: String?,
    val accessDescriptorMimeType: String?,
    val accessDescriptorProvenance: String?,
    val accessDescriptorGenerated: Boolean?,
    val accessDescriptorExpiresAtEpochMillis: Long?,
    val accessProvenance: String?,
    val accessCheckedAtEpochMillis: Long?,
    val isSaved: Boolean,
)

internal const val PAPER_ROW_COLUMNS = """
    p.id AS id, p.title AS title, p.authors AS authors, p.venue AS venue,
    p.publicationYear AS publicationYear, p.type AS type, p.topics AS topics,
    p.citationCount AS citationCount, p.abstractText AS abstractText,
    a.state AS accessState, a.sourceUrl AS accessSourceUrl, a.descriptorUrl AS accessDescriptorUrl,
    a.descriptorMimeType AS accessDescriptorMimeType, a.descriptorProvenance AS accessDescriptorProvenance,
    a.descriptorGenerated AS accessDescriptorGenerated,
    a.descriptorExpiresAtEpochMillis AS accessDescriptorExpiresAtEpochMillis,
    a.provenance AS accessProvenance, a.checkedAtEpochMillis AS accessCheckedAtEpochMillis
"""
