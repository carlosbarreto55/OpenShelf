package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}
