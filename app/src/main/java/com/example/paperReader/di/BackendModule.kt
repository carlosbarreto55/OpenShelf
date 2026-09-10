package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.data.remote.RemoteJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object BackendModule {
    @Provides
    @Singleton
    fun provideBackendConfiguration(): BackendConfiguration {
        return BackendConfiguration.fromBuildConfig()
    }

    /** Only ever requested in an HTTP build: [providePaperRemoteDataSource] resolves the fake
     * source without touching this provider, so there is no placeholder base URL to fall back to. */
    @Provides
    @Singleton
    fun providePaperReaderApi(configuration: BackendConfiguration): PaperReaderApi {
        return Retrofit.Builder()
            .baseUrl(configuration.requireBaseUrl())
            .addConverterFactory(RemoteJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PaperReaderApi::class.java)
    }

    @Provides
    @Singleton
    fun providePaperRemoteDataSource(
        configuration: BackendConfiguration,
        fakeProvider: Provider<FakePaperRemoteDataSource>,
        httpProvider: Provider<HttpPaperRemoteDataSource>,
    ): PaperRemoteDataSource {
        return when (configuration.mode) {
            BackendMode.HTTP -> httpProvider.get()
            BackendMode.FAKE -> fakeProvider.get()
        }
    }
}
