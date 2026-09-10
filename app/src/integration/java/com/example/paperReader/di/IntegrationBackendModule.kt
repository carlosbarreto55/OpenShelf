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
 * The integration flavor talks to a real PaperReader server and nothing else.
 *
 * There is no fake implementation on this source set's classpath, so "accidentally serving demo
 * data" is not a runtime state this build can reach — it is a compile error.
 */
@Module
@InstallIn(SingletonComponent::class)
object IntegrationBackendModule {
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
abstract class IntegrationBackendBindsModule {
    @Binds
    @Singleton
    abstract fun bindPaperRemoteDataSource(impl: HttpPaperRemoteDataSource): PaperRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindDownloadGateway(impl: WorkManagerDownloadGateway): DownloadGateway
}
