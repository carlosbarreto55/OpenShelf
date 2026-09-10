package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The backend wiring both flavors share.
 *
 * Which [com.example.paperReader.data.remote.PaperRemoteDataSource] and
 * [com.example.paperReader.domain.repository.DownloadGateway] the app gets is **not** decided here.
 * It is decided by which source set is compiled: `src/demo` binds the fakes, `src/integration` binds
 * the HTTP and WorkManager implementations. That keeps the demo implementations out of the
 * integration APK entirely, rather than shipping them as unreachable code behind a runtime `when`.
 *
 * [BackendConfiguration] still resolves and validates the configured mode and base URL, so a
 * misconfigured integration build fails loudly instead of quietly serving something else.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackendModule {
    @Provides
    @Singleton
    fun provideBackendConfiguration(): BackendConfiguration {
        return BackendConfiguration.fromBuildConfig()
    }
}
