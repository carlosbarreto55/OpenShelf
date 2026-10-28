package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.data.remote.RemoteJson
import com.example.paperReader.data.download.WorkManagerDownloadGateway
import com.example.paperReader.domain.repository.DownloadGateway
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

/**
 * Every flavor that talks to a real PaperReader server shares this wiring: `integration`, which
 * takes its address from a build property, and `production`, whose address is a build constant.
 * They differ only in where the URL comes from, so they must not differ in how it is used — one
 * module, compiled into both, is what keeps that true.
 *
 * There is no fake implementation on this source set's classpath, so "accidentally serving demo
 * data" is not a runtime state either build can reach — it is a compile error.
 */
@Module
@InstallIn(SingletonComponent::class)
object HttpBackendModule {
    @Provides
    @Singleton
    fun providePaperReaderApi(configuration: BackendConfiguration): PaperReaderApi {
        return Retrofit.Builder()
            .baseUrl(configuration.requireBaseUrl())
            .addConverterFactory(RemoteJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PaperReaderApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HttpBackendBindsModule {
    @Binds
    @Singleton
    abstract fun bindPaperRemoteDataSource(impl: HttpPaperRemoteDataSource): PaperRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindDownloadGateway(impl: WorkManagerDownloadGateway): DownloadGateway
}
