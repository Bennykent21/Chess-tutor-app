package com.chesstutor.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** [SettingsStore] backed by Jetpack DataStore (Preferences). */
class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val store = context.applicationContext.appSettingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { error ->
            // A damaged file must not crash the app: fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> AppSettingsCodec.decode(prefs.toNamedMap()) }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(AppSettingsCodec.decode(prefs.toNamedMap()))
            prefs.write(AppSettingsCodec.encode(next))
        }
    }

    override suspend fun importLegacyOnce(soundEnabled: Boolean) {
        val importedKey = booleanPreferencesKey(AppSettingsCodec.LEGACY_IMPORTED)
        store.edit { prefs ->
            if (prefs[importedKey] == true) return@edit
            prefs[booleanPreferencesKey(AppSettingsCodec.SOUND)] = soundEnabled
            prefs[importedKey] = true
        }
    }

    private fun Preferences.toNamedMap(): Map<String, Any?> =
        asMap().entries.associate { (key, value) -> key.name to value }

    private fun MutablePreferences.write(values: Map<String, Any>) {
        values.forEach { (name, value) ->
            when (value) {
                is Boolean -> this[booleanPreferencesKey(name)] = value
                is String -> this[stringPreferencesKey(name)] = value
            }
        }
    }
}
