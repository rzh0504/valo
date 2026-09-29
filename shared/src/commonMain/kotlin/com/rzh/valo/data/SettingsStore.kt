package com.rzh.valo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** 深色模式选择 */
enum class ThemeMode(val id: Int, val label: String) {
    SYSTEM(0, "跟随系统"),
    LIGHT(1, "浅色"),
    DARK(2, "深色");

    companion object {
        fun from(id: Int): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

val MATCH_LEVELS = listOf("S", "A", "B", "C")

/** 按用户勾选的赛事级别过滤；接口未标注级别（或级别不在已知集合内）的比赛始终保留 */
fun List<MatchItem>.filterByLevels(enabled: Set<String>): List<MatchItem> = filter { match ->
    val level = match.level?.uppercase()
    level !in MATCH_LEVELS || level in enabled
}

/** 应用设置（DataStore 持久化，文件与应用内目录沿用以兼容旧版升级） */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private val themeKey = intPreferencesKey("theme_mode")
    private val widgetOpacityKey = floatPreferencesKey("widget_opacity")
    private val matchLevelsKey = stringSetPreferencesKey("match_levels")

    val themeMode: Flow<ThemeMode> = dataStore.data.map {
        ThemeMode.from(it[themeKey] ?: ThemeMode.SYSTEM.id)
    }

    /** 桌面小组件背景不透明度（0.2 ~ 1.0） */
    val widgetOpacity: Flow<Float> = dataStore.data.map { it[widgetOpacityKey] ?: 1f }

    /** 赛程中保留的赛事级别；接口未提供级别的比赛始终保留。 */
    val matchLevels: Flow<Set<String>> = dataStore.data.map {
        (it[matchLevelsKey] ?: MATCH_LEVELS.toSet()).intersect(MATCH_LEVELS.toSet())
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeKey] = mode.id }
    }

    suspend fun setWidgetOpacity(value: Float) {
        dataStore.edit { it[widgetOpacityKey] = value.coerceIn(0.2f, 1f) }
    }

    suspend fun setMatchLevel(level: String, enabled: Boolean) {
        if (level !in MATCH_LEVELS) return
        dataStore.edit {
            val levels = (it[matchLevelsKey] ?: MATCH_LEVELS.toSet()).toMutableSet()
            if (enabled) levels += level else levels -= level
            it[matchLevelsKey] = levels
        }
    }

    companion object {
        /** [filesDir] 为各平台提供的应用私有目录，文件布局与旧版 Android 一致 */
        fun create(filesDir: String): SettingsStore {
            val dataStore = PreferenceDataStoreFactory.createWithPath(
                produceFile = { "$filesDir/datastore/settings.preferences_pb".toPath() },
            )
            return SettingsStore(dataStore)
        }
    }
}
