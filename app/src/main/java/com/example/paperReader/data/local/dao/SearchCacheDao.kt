package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.paperReader.data.local.entity.AccessSnapshotEntity
import com.example.paperReader.data.local.entity.PAPER_ROW_COLUMNS
import com.example.paperReader.data.local.entity.PaperEntity
import com.example.paperReader.data.local.entity.PaperRow
import com.example.paperReader.data.local.entity.SearchQueryEntity
import com.example.paperReader.data.local.entity.SearchResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_queries WHERE queryKey = :queryKey")
    suspend fun getQuery(queryKey: String): SearchQueryEntity?

    @Query("SELECT * FROM search_queries WHERE queryKey = :queryKey")
    fun observeQuery(queryKey: String): Flow<SearchQueryEntity?>

    @Query("SELECT COUNT(*) FROM search_results WHERE queryKey = :queryKey")
    suspend fun countResults(queryKey: String): Int

    /** The next append starts after this, never after `COUNT(*)`: migrated rows can leave gaps in
     * the position sequence, and reusing a taken position would reorder the cached results. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM search_results WHERE queryKey = :queryKey")
    suspend fun maxPosition(queryKey: String): Int

    /** The papers this query has already claimed a position for, so an overlapping page can be
     * narrowed to the IDs that are genuinely new. */
    @Query("SELECT paperId FROM search_results WHERE queryKey = :queryKey")
    suspend fun associatedPaperIds(queryKey: String): List<String>

    /**
     * Persists one mapped page as a single logical operation: query metadata (cursor and total),
     * paper rows, access snapshots and ordered cross-references either all land or none do.
     *
     * [orderedPaperIds] is the page in backend order. Deduplication happens here rather than in the
     * caller so the "already associated" read and the insert cannot interleave with another page.
     * A first page replaces only *this* query's associations; an append keeps every existing
     * position and adds unseen IDs after the current maximum.
     */
    @Transaction
    suspend fun persistPage(
        query: SearchQueryEntity,
        papers: List<PaperEntity>,
        accessSnapshots: List<AccessSnapshotEntity>,
        orderedPaperIds: List<String>,
        isFirstPage: Boolean,
    ) {
        if (isFirstPage) {
            deleteResultsFor(query.queryKey)
        }
        upsertQuery(query)
        upsertPapers(papers)
        accessSnapshots.forEach { upsertAccessSnapshot(it) }

        val alreadyAssociated = if (isFirstPage) emptySet() else associatedPaperIds(query.queryKey).toSet()
        val seen = alreadyAssociated.toMutableSet()
        var position = if (isFirstPage) -1 else maxPosition(query.queryKey)
        val newAssociations = orderedPaperIds.mapNotNull { paperId ->
            // `seen` covers both repeats inside this page and overlap with earlier pages; the
            // first occurrence is the one that keeps its place.
            if (!seen.add(paperId)) return@mapNotNull null
            position += 1
            SearchResultEntity(queryKey = query.queryKey, position = position, paperId = paperId)
        }
        insertResults(newAssociations)
    }

    /** Update-or-insert, not REPLACE: REPLACE deletes the conflicting row before reinserting it,
     * which would cascade-delete this query's own `search_results` (FK `onDelete=CASCADE`) right
     * before we insert the new page's cross-references. */
    suspend fun upsertQuery(query: SearchQueryEntity) {
        if (updateQuery(query) == 0) insertQueryIgnoringConflict(query)
    }

    @Update
    suspend fun updateQuery(query: SearchQueryEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueryIgnoringConflict(query: SearchQueryEntity): Long

    /** Update-or-insert for the same reason as [upsertQuery]: REPLACE would cascade-delete the
     * paper's `access_snapshots` row every time it is simply re-cached. */
    suspend fun upsertPapers(papers: List<PaperEntity>) {
        papers.forEach { paper ->
            if (updatePaper(paper) == 0) insertPaperIgnoringConflict(paper)
        }
    }

    @Update
    suspend fun updatePaper(paper: PaperEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaperIgnoringConflict(paper: PaperEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<SearchResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccessSnapshot(snapshot: AccessSnapshotEntity)

    @Query("DELETE FROM search_results WHERE queryKey = :queryKey")
    suspend fun deleteResultsFor(queryKey: String)

    @Query(
        """
        SELECT $PAPER_ROW_COLUMNS,
          CASE WHEN s.paperId IS NULL THEN 0 ELSE 1 END AS isSaved
        FROM papers p
        INNER JOIN search_results sr ON sr.paperId = p.id
        LEFT JOIN access_snapshots a ON a.paperId = p.id
        LEFT JOIN saved_papers s ON s.paperId = p.id
        WHERE sr.queryKey = :queryKey
        ORDER BY sr.position ASC
        """,
    )
    fun observeResults(queryKey: String): Flow<List<PaperRow>>

    /** Cascades to [SearchResultEntity] rows for expired queries; does not touch the papers table. */
    @Query("DELETE FROM search_queries WHERE fetchedAtEpochMillis < :olderThanEpochMillis")
    suspend fun deleteStaleQueries(olderThanEpochMillis: Long): Int
}
