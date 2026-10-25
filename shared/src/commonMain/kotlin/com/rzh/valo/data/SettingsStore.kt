package com.rzh.valo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

/** 底部导航栏样式 */
enum class NavBarStyle(val id: Int, val label: String) {
    DEFAULT(0, "默认"),
    FLOATING(1, "悬浮");

    companion object {
        fun from(id: Int): NavBarStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 按屏蔽的系列赛过滤；[seriesMap] 为 tournament_id → 系列赛 id 映射，
 * 映射不到（赛事未收录或无系列归属）的比赛始终保留。
 */
fun List<MatchItem>.filterBySeries(seriesMap: Map<String, String>, hidden: Set<String>): List<MatchItem> {
    if (hidden.isEmpty()) return this
    return filter { match ->
        val seriesId = match.tournament?.id?.let(seriesMap::get)
        seriesId == null || seriesId !in hidden
    }
}

/** 赛事页持久化的筛选状态（「保存筛选」开关开启时写入/恢复） */
data class TournamentSavedFilters(
    val zone: String = TournamentZones.ALL,
    val level: String? = null,
    val year: Int? = null,
    val status: Int = 0,
)

/** 应用设置（DataStore 持久化，文件与应用内目录沿用以兼容旧版升级） */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private val themeKey = intPreferencesKey("theme_mode")
    private val hiddenSeriesKey = stringSetPreferencesKey("schedule_hidden_series")
    private val navBarStyleKey = intPreferencesKey("nav_bar_style")
    private val tournamentSaveFiltersKey = booleanPreferencesKey("tournament_save_filters")
    private val tournamentZoneKey = stringPreferencesKey("tournament_zone")
    private val tournamentLevelKey = stringPreferencesKey("tournament_level")
    private val tournamentYearKey = intPreferencesKey("tournament_year")
    private val tournamentStatusKey = intPreferencesKey("tournament_status")

    val themeMode: Flow<ThemeMode> = dataStore.data.map {
        ThemeMode.from(it[themeKey] ?: ThemeMode.SYSTEM.id)
    }

    val navigationBarStyle: Flow<NavBarStyle> = dataStore.data.map {
        NavBarStyle.from(it[navBarStyleKey] ?: NavBarStyle.DEFAULT.id)
    }

    /** 赛程中屏蔽的系列赛 id；未写入时默认屏蔽目录中的非热门系列，目录外系列始终显示。 */
    val scheduleHiddenSeries: Flow<Set<String>> = dataStore.data
        // 赛程渲染以本流就绪为前提，读取异常时回退默认值而不是让界面一直等待
        .catch { e ->
            if (e is CancellationException) throw e
            emit(emptyPreferences())
        }
        .map { it[hiddenSeriesKey] ?: DEFAULT_HIDDEN_SERIES }

    /** 赛事页「保存筛选」开关（默认关闭，关闭时不读写筛选值） */
    val tournamentFiltersSaved: Flow<Boolean> = dataStore.data.map {
        it[tournamentSaveFiltersKey] ?: false
    }

    /** 赛事页筛选值的持久化快照 */
    val tournamentFilters: Flow<TournamentSavedFilters> = dataStore.data.map {
        TournamentSavedFilters(
            zone = it[tournamentZoneKey] ?: TournamentZones.ALL,
            level = it[tournamentLevelKey]?.takeIf { level -> level.isNotBlank() },
            year = it[tournamentYearKey]?.takeIf { year -> year != 0 },
            status = it[tournamentStatusKey] ?: 0,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeKey] = mode.id }
    }

    suspend fun setNavigationBarStyle(style: NavBarStyle) {
        dataStore.edit { it[navBarStyleKey] = style.id }
    }

    suspend fun setTournamentFiltersSaved(enabled: Boolean) {
        dataStore.edit { it[tournamentSaveFiltersKey] = enabled }
    }

    suspend fun setTournamentFilters(filters: TournamentSavedFilters) {
        dataStore.edit {
            it[tournamentZoneKey] = filters.zone
            it[tournamentLevelKey] = filters.level.orEmpty()
            it[tournamentYearKey] = filters.year ?: 0
            it[tournamentStatusKey] = filters.status
        }
    }

    /** 记录/取消屏蔽一个系列赛（[hidden] 为 true 表示屏蔽）；首次编辑以默认屏蔽集为基底 */
    suspend fun setSeriesHidden(id: String, hidden: Boolean) {
        if (id.isBlank()) return
        dataStore.edit {
            val series = (it[hiddenSeriesKey] ?: DEFAULT_HIDDEN_SERIES).toMutableSet()
            if (hidden) series += id else series -= id
            it[hiddenSeriesKey] = series
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
