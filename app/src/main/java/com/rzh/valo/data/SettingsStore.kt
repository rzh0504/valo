package com.rzh.valo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 深色模式选择 */
enum class ThemeMode(val id: Int, val label: String) {
    SYSTEM(0, "跟随系统"),
    LIGHT(1, "浅色"),
    DARK(2, "深色");

    companion object {
        fun from(id: Int): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 应用设置（DataStore 持久化） */
class SettingsStore(private val context: Context) {

    private val themeKey = intPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.from(it[themeKey] ?: ThemeMode.SYSTEM.id)
    }

    /** Material You 动态取色，Android 12+ 有效 */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[dynamicKey] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.id }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = enabled }
    }
}
