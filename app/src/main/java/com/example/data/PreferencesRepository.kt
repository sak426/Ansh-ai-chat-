package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studySparkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "studyspark_prefs"
)

data class CachedBranding(
    val appName: String = DEFAULT_APP_NAME,
    val logoBase64: String = ""
)

class PreferencesRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val cachedAppNameKey = stringPreferencesKey("cached_app_name")
    private val cachedLogoBase64Key = stringPreferencesKey("cached_logo_base64")

    val themeMode: Flow<ThemeMode> = context.studySparkDataStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[themeModeKey])
    }

    val cachedBranding: Flow<CachedBranding> = context.studySparkDataStore.data.map { prefs ->
        val rawName = prefs[cachedAppNameKey]
        val resolvedName = if (rawName.isNullOrBlank() || rawName.equals("StudySpark", ignoreCase = true)) {
            DEFAULT_APP_NAME
        } else {
            rawName
        }
        CachedBranding(
            appName = resolvedName,
            logoBase64 = prefs[cachedLogoBase64Key].orEmpty()
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.studySparkDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun cacheBranding(appName: String, logoBase64: String) {
        val cleanName = if (appName.isBlank() || appName.equals("StudySpark", ignoreCase = true)) {
            DEFAULT_APP_NAME
        } else {
            appName
        }
        context.studySparkDataStore.edit { prefs ->
            prefs[cachedAppNameKey] = cleanName
            prefs[cachedLogoBase64Key] = logoBase64
        }
    }
}
