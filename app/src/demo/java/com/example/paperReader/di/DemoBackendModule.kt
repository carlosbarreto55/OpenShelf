package com.example.paperReader.di

import com.example.paperReader.data.download.FakeDownloadManager
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.domain.repository.DownloadGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The demo flavor serves fixture data and simulates downloads, with no backend and no `INTERNET`
 * permission. It never constructs Retrofit, so there is no placeholder base URL to fall back to.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DemoBackendModule {
    @Binds
    @Singleton
    abstract fun bindPaperRemoteDataSource(impl: FakePaperRemoteDataSource): PaperRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindDownloadGateway(impl: FakeDownloadManager): DownloadGateway
}
