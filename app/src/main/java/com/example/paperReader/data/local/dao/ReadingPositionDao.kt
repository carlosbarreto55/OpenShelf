package com.example.paperReader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.paperReader.data.local.entity.ReadingPositionEntity
import com.example.paperReader.data.local.entity.ResumableReadingRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE paperId = :paperId")
    suspend fun get(paperId: String): ReadingPositionEntity?

    /** A true upsert, not `REPLACE`: the row is updated in place, never deleted and re-inserted. */
    @Upsert
    suspend fun upsert(entity: ReadingPositionEntity)

    /**
     * Papers that can be picked up where they were left: a stored position *and* a completed
     * download that still names a file, most recently read first. Whether that file is still on
     * disk is a filesystem question this query cannot answer, so the repository checks it too.
     */
    @Query(
        """
        SELECT rp.paperId AS paperId, d.id AS downloadId, d.title AS title,
            d.localContentUri AS localContentUri, rp.pageIndex AS pageIndex,
            rp.pageOffsetFraction AS pageOffsetFraction, rp.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM reading_positions rp
        INNER JOIN downloads d ON d.paperId = rp.paperId
        WHERE d.state = 'COMPLETED' AND d.localContentUri IS NOT NULL
        ORDER BY rp.updatedAtEpochMillis DESC
        """,
    )
    fun observeResumable(): Flow<List<ResumableReadingRow>>
}
