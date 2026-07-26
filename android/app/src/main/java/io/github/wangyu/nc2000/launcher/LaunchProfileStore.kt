package io.github.wangyu.nc2000.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.launchProfileDataStore by preferencesDataStore(name = "launch_profiles")

class LaunchProfileStore(private val context: Context) {
    private val initializedKey = booleanPreferencesKey("initialized")
    private val profilesKey = stringPreferencesKey("profiles_json")

    val profiles: Flow<List<LaunchProfile>> = context.launchProfileDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            if (preferences[initializedKey] != true) {
                listOf(LaunchProfile.defaultNc1020())
            } else {
                LaunchProfileJson.decode(preferences[profilesKey].orEmpty())
            }
        }

    suspend fun initialize() {
        val preferences = context.launchProfileDataStore.data.first()
        if (preferences[initializedKey] == true) return
        save(listOf(LaunchProfile.defaultNc1020()))
    }

    suspend fun save(profiles: List<LaunchProfile>) {
        context.launchProfileDataStore.edit { preferences ->
            preferences[initializedKey] = true
            preferences[profilesKey] = LaunchProfileJson.encode(profiles)
        }
    }
}
