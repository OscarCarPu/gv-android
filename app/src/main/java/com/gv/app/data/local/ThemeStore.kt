package com.gv.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePreference { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Persists the user's light/dark/follow-system choice. */
class ThemeStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore
    private val key = stringPreferencesKey("theme_preference")

    val theme: Flow<ThemePreference> = dataStore.data.map { prefs ->
        runCatching { ThemePreference.valueOf(prefs[key] ?: ThemePreference.DARK.name) }
            .getOrDefault(ThemePreference.DARK)
    }

    suspend fun set(preference: ThemePreference) {
        dataStore.edit { it[key] = preference.name }
    }
}
