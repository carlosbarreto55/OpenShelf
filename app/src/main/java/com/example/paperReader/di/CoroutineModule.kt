package com.example.paperReader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A process-lifetime scope for work that must outlive any single screen — namely, simulating a
 * download's progress independently of whether the Downloads screen is currently on screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Blocking file and renderer work, injected so a test can run it on a test dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
