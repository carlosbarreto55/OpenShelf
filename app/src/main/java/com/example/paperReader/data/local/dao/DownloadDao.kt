package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.paperReader.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    /**
     * Removes a record and the reading position stored for its paper, together. The position goes
     * first, while the record that names its paper still exists, so "continue reading" can never
     * offer a paper whose download was removed (design.md decision 3).
     */
    @Transaction
    suspend fun delete(id: String) {
        deleteReadingPositionFor(id)
        deleteRecord(id)
    }

    @Query("DELETE FROM reading_positions WHERE paperId IN (SELECT paperId FROM downloads WHERE id = :id)")
    suspend fun deleteReadingPositionFor(id: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteRecord(id: String)
}
