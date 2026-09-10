package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.paperReader.data.local.entity.PAPER_ROW_COLUMNS
import com.example.paperReader.data.local.entity.PaperEntity
import com.example.paperReader.data.local.entity.PaperRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {
    /** Deliberately not `OnConflictStrategy.REPLACE`: REPLACE deletes the conflicting row before
     * reinserting it, which would cascade-delete its `access_snapshots` row (FK `onDelete=CASCADE`)
     * every time a paper is simply re-cached. Update-or-insert avoids that delete entirely. */
    @Transaction
    suspend fun upsertAll(papers: List<PaperEntity>) {
        papers.forEach { paper ->
            if (updatePaper(paper) == 0) insertPaperIgnoringConflict(paper)
        }
    }

    @Update
    suspend fun updatePaper(paper: PaperEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaperIgnoringConflict(paper: PaperEntity): Long

    @Query("SELECT * FROM papers WHERE id = :paperId")
    suspend fun get(paperId: String): PaperEntity?

    @Query(
        """
        SELECT $PAPER_ROW_COLUMNS,
          CASE WHEN s.paperId IS NULL THEN 0 ELSE 1 END AS isSaved
        FROM papers p
        LEFT JOIN access_snapshots a ON a.paperId = p.id
        LEFT JOIN saved_papers s ON s.paperId = p.id
        WHERE p.id = :paperId
        """,
    )
    fun observeRow(paperId: String): Flow<PaperRow?>

    /** Removes papers no longer referenced by any cached search result, saved entry, or download —
     * the only place paper rows are ever deleted, keeping saved/download-owned records safe. */
    @Query(
        """
        DELETE FROM papers WHERE id NOT IN (SELECT paperId FROM saved_papers)
          AND id NOT IN (SELECT paperId FROM downloads)
          AND id NOT IN (SELECT paperId FROM search_results)
        """,
    )
    suspend fun deleteOrphanedPapers(): Int
}
