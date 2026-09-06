package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
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

    @Provides
    @Singleton
    fun providePaperReaderApi(configuration: BackendConfiguration): PaperReaderApi {
        val baseUrl = configuration.baseUrl
            ?.ensureTrailingSlash()
            ?: "https://paperreader.invalid/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
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
        return if (configuration.shouldUseHttp) httpProvider.get() else fakeProvider.get()
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith('/')) this else "$this/"
    }
}
