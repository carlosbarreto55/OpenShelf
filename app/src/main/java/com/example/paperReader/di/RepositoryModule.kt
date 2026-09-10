package com.example.paperReader.di

import com.example.paperReader.core.Clock
import com.example.paperReader.core.SystemClock
import com.example.paperReader.data.repository.DownloadsRepositoryImpl
import com.example.paperReader.data.repository.LibraryRepositoryImpl
import com.example.paperReader.data.repository.PaperRepositoryImpl
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.repository.PaperRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPaperRepository(impl: PaperRepositoryImpl): PaperRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindDownloadsRepository(impl: DownloadsRepositoryImpl): DownloadsRepository

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = SystemClock
    }
}
