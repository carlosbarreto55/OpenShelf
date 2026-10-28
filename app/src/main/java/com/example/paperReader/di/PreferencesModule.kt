package com.example.paperReader.di

import android.content.Context
import com.example.paperReader.core.preferences.DemoDisclosurePreferences
import com.example.paperReader.data.preferences.DataStoreDemoDisclosurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Small, non-relational user state. Bound in `main` rather than per flavor: the *storage* is the
 * same everywhere, and it is [com.example.paperReader.core.config.BackendConfiguration] that
 * decides whether a demo disclosure is owed at all. */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun provideDemoDisclosurePreferences(
        @ApplicationContext context: Context,
    ): DemoDisclosurePreferences = DataStoreDemoDisclosurePreferences(context)
}
