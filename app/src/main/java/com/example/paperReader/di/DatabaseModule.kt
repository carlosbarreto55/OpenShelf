package com.example.paperReader.di

import android.content.Context
import androidx.room.Room
import com.example.paperReader.data.local.MIGRATION_1_2
import com.example.paperReader.data.local.MIGRATION_2_3
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.dao.AccessSnapshotDao
import com.example.paperReader.data.local.dao.DownloadDao
import com.example.paperReader.data.local.dao.PaperDao
import com.example.paperReader.data.local.dao.RecentSearchDao
import com.example.paperReader.data.local.dao.SavedPaperDao
import com.example.paperReader.data.local.dao.SearchCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaperReaderDatabase {
        return Room.databaseBuilder(context, PaperReaderDatabase::class.java, PaperReaderDatabase.DATABASE_NAME)
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun providePaperDao(database: PaperReaderDatabase): PaperDao = database.paperDao()

    @Provides
    fun provideAccessSnapshotDao(database: PaperReaderDatabase): AccessSnapshotDao = database.accessSnapshotDao()

    @Provides
    fun provideSearchCacheDao(database: PaperReaderDatabase): SearchCacheDao = database.searchCacheDao()

    @Provides
    fun provideRecentSearchDao(database: PaperReaderDatabase): RecentSearchDao = database.recentSearchDao()

    @Provides
    fun provideSavedPaperDao(database: PaperReaderDatabase): SavedPaperDao = database.savedPaperDao()

    @Provides
    fun provideDownloadDao(database: PaperReaderDatabase): DownloadDao = database.downloadDao()
}
