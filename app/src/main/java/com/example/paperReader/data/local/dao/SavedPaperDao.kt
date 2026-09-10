package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.paperReader.data.local.entity.PAPER_ROW_COLUMNS
import com.example.paperReader.data.local.entity.PaperRow
import com.example.paperReader.data.local.entity.SavedPaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPaperDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SavedPaperEntity)

    @Query("DELETE FROM saved_papers WHERE paperId = :paperId")
    suspend fun delete(paperId: String)

    @Query("SELECT paperId FROM saved_papers WHERE paperId = :paperId")
    suspend fun get(paperId: String): String?

    @Query(
        """
        SELECT $PAPER_ROW_COLUMNS, 1 AS isSaved
        FROM papers p
        INNER JOIN saved_papers s ON s.paperId = p.id
        LEFT JOIN access_snapshots a ON a.paperId = p.id
        ORDER BY s.savedAtEpochMillis DESC
        """,
    )
    fun observeSaved(): Flow<List<PaperRow>>
}
