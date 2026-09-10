package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.paperReader.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    /** REPLACE on the queryText primary key both deduplicates and moves the entry to the most
     * recent position once ordered by [RecentSearchEntity.lastUsedAtEpochMillis]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentSearchEntity)

    @Query("SELECT * FROM recent_searches ORDER BY lastUsedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM recent_searches WHERE queryText NOT IN (
            SELECT queryText FROM recent_searches ORDER BY lastUsedAtEpochMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimToMostRecent(keep: Int)
}
