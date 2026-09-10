package com.example.paperReader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: [com.example.paperReader.data.local.entity.DownloadEntity] gained `sourceUrl`
 * (the confirmed descriptor a download was started from, needed to requeue a retry) and
 * `mimeType` (the completed file's actual type). `sourceUrl` is NOT NULL, so existing rows get an
 * empty placeholder — they predate the column and were never going to be retried against it. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE downloads ADD COLUMN mimeType TEXT")
    }
}

/**
 * v2 -> v3: one cached result per `(queryKey, paperId)`.
 *
 * Existing databases can hold the same paper at several positions for one query, so the duplicates
 * are removed *before* the unique index is created — keeping the lowest position, which is the
 * paper's first-seen place in backend order. Only the query/paper cross-reference rows are touched:
 * papers, saved state, access snapshots and downloads are user- or cache-owned data and survive
 * unchanged.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM search_results
            WHERE rowid NOT IN (
                SELECT sr.rowid FROM search_results sr
                WHERE sr.position = (
                    SELECT MIN(dup.position) FROM search_results dup
                    WHERE dup.queryKey = sr.queryKey AND dup.paperId = sr.paperId
                )
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_search_results_queryKey_paperId " +
                "ON search_results (queryKey, paperId)",
        )
    }
}
