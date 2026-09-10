package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.paperReader.data.local.entity.AccessSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: AccessSnapshotEntity)

    @Query("SELECT * FROM access_snapshots WHERE paperId = :paperId")
    suspend fun get(paperId: String): AccessSnapshotEntity?

    @Query("SELECT * FROM access_snapshots WHERE paperId = :paperId")
    fun observe(paperId: String): Flow<AccessSnapshotEntity?>
}
