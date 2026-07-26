package io.github.wangyu.nc2000.controls

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

private val Context.controlSceneDataStore by preferencesDataStore(name = "control_scenes")

class ControlSceneStore(private val context: Context) {
    private val initializedKey = booleanPreferencesKey("initialized")
    private val scenesKey = stringPreferencesKey("scenes_json")

    val scenes: Flow<List<ControlScene>> = context.controlSceneDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            if (preferences[initializedKey] != true) defaultScenes()
            else ControlSceneJson.decode(preferences[scenesKey].orEmpty())
        }

    suspend fun initialize() {
        val preferences = context.controlSceneDataStore.data.first()
        if (preferences[initializedKey] == true) return
        save(defaultScenes())
    }

    suspend fun save(scenes: List<ControlScene>) {
        require(scenes.flatMap { it.validationErrors() }.isEmpty()) { "场景布局配置无效" }
        context.controlSceneDataStore.edit { preferences ->
            preferences[initializedKey] = true
            preferences[scenesKey] = ControlSceneJson.encode(scenes)
        }
    }

    private fun defaultScenes() = listOf(ControlScene.defaultGameOverlay())
}
