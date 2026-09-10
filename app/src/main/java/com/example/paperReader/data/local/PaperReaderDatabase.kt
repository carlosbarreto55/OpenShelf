package com.example.paperReader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.paperReader.data.local.dao.AccessSnapshotDao
import com.example.paperReader.data.local.dao.DownloadDao
import com.example.paperReader.data.local.dao.PaperDao
import com.example.paperReader.data.local.dao.RecentSearchDao
import com.example.paperReader.data.local.dao.SavedPaperDao
import com.example.paperReader.data.local.dao.SearchCacheDao
import com.example.paperReader.data.local.entity.AccessSnapshotEntity
import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.data.local.entity.PaperEntity
import com.example.paperReader.data.local.entity.RecentSearchEntity
import com.example.paperReader.data.local.entity.SavedPaperEntity
import com.example.paperReader.data.local.entity.SearchQueryEntity
import com.example.paperReader.data.local.entity.SearchResultEntity

@Database(
    entities = [
        PaperEntity::class,
        AccessSnapshotEntity::class,
        SearchQueryEntity::class,
        SearchResultEntity::class,
        RecentSearchEntity::class,
        SavedPaperEntity::class,
        DownloadEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PaperReaderDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao

    abstract fun accessSnapshotDao(): AccessSnapshotDao

    abstract fun searchCacheDao(): SearchCacheDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun savedPaperDao(): SavedPaperDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "paperreader.db"

        /** Room does not enable SQLite foreign-key enforcement by default; without this, the
         * `ON DELETE CASCADE` on search_results/access_snapshots would silently not fire. */
        val enableForeignKeysCallback: Callback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}
