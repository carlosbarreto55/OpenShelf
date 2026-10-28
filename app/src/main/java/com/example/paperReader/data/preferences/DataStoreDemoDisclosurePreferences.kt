package com.example.paperReader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.paperReader.core.preferences.DemoDisclosurePreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.demoDisclosureDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "demo_disclosure",
)

@Singleton
class DataStoreDemoDisclosurePreferences @Inject constructor(
    private val context: Context,
) : DemoDisclosurePreferences {

    override val isAcknowledged: Flow<Boolean> = context.demoDisclosureDataStore.data
        // An unreadable store means the acknowledgement is unknown, and the honest answer to
        // "has the user been told this is demo data?" is then no: show the explanation again
        // rather than suppress a disclosure on the strength of a failed read.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences -> preferences[ACKNOWLEDGED] == true }

    override suspend fun acknowledge() {
        context.demoDisclosureDataStore.edit { preferences -> preferences[ACKNOWLEDGED] = true }
    }

    private companion object {
        val ACKNOWLEDGED = booleanPreferencesKey("demo_explanation_acknowledged")
    }
}
