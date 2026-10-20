package com.rzh.valo.ui.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.TournamentItem
import com.rzh.valo.data.TournamentSavedFilters
import com.rzh.valo.data.TournamentZones
import com.rzh.valo.data.epochToLocalDate
import com.rzh.valo.data.nowMillis
import com.rzh.valo.data.tournamentZoneBucket
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TournamentListUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<TournamentItem> = emptyList(),
    /** 赛区大区桶，TournamentZones.ALL / GLOBAL / CHINA / PACIFIC / EMEA / AMERICAS */
    val zone: String = TournamentZones.ALL,
    /** 等级：null 全部 / S / A / B / C */
    val level: String? = null,
    /** 年份：null 全部 */
    val year: Int? = null,
    /** 状态：0 全部 / 1 未开始 / 2 进行中 / 3 已结束 */
    val status: Int = 0,
    /** 「保存筛选」开关：开启时筛选变化写入 DataStore，下次启动恢复 */
    val saveFilters: Boolean = false,
) {
    /** 有赛事数据的年份（新在前），供年份筛选行 */
    val yearOptions: List<Int>
        get() = items.map { epochToLocalDate(it.startDate).year }.distinct().sortedDescending()

    /** 按年份分组（新年份在前，年内新赛事在前） */
    val groups: List<TournamentYearGroup>
        get() = items.asSequence()
            .filter { it.startDate > 0L }
            .filter { zone == TournamentZones.ALL || tournamentZoneBucket(it.zoneName) == zone }
            .filter { level == null || it.level == level }
            .filter { year == null || epochToLocalDate(it.startDate).year == year }
            .filter { status == 0 || it.status == status }
            .groupBy { epochToLocalDate(it.startDate).year }
            .map { (y, list) -> TournamentYearGroup(y, list.sortedByDescending { it.startDate }) }
            .sortedByDescending { it.year }
}

data class TournamentYearGroup(val year: Int, val tournaments: List<TournamentItem>)

class TournamentListViewModel(
    private val repository: MatchRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TournamentListUiState())
    val state = _state.asStateFlow()

    /** 上次发起联网加载的时间，作为回前台是否再验证的依据 */
    private var lastRequestedAt = 0L

    init {
        loadInternal(force = false)
        viewModelScope.launch {
            // 「保存筛选」开启时，恢复开关状态与上次持久化的筛选值
            if (settingsStore.tournamentFiltersSaved.first()) {
                val saved = settingsStore.tournamentFilters.first()
                _state.update {
                    it.copy(
                        saveFilters = true,
                        zone = saved.zone,
                        level = saved.level,
                        year = saved.year,
                        status = saved.status,
                    )
                }
            }
        }
    }

    /** 下拉刷新：强制绕过接口层缓存并显示下拉指示器 */
    fun refresh() = loadInternal(force = true)

    /** 回到前台：距上次发起加载超过接口层缓存 TTL 时静默再验证 */
    fun onResume() {
        if (nowMillis() - lastRequestedAt > MatchRepository.WINDOW_TTL) {
            loadInternal(force = false)
        }
    }

    /** 「保存筛选」开关：开启即把当前筛选写入持久化，关闭后停止写入（内存筛选保持） */
    fun setSaveFilters(enabled: Boolean) {
        _state.update { it.copy(saveFilters = enabled) }
        viewModelScope.launch {
            settingsStore.setTournamentFiltersSaved(enabled)
            if (enabled) persistFilters()
        }
    }

    fun selectZone(zone: String) {
        _state.update { it.copy(zone = zone) }
        persistIfEnabled()
    }

    fun selectLevel(level: String?) {
        _state.update { it.copy(level = level) }
        persistIfEnabled()
    }

    fun selectYear(year: Int?) {
        _state.update { it.copy(year = year) }
        persistIfEnabled()
    }

    fun selectStatus(status: Int) {
        _state.update { it.copy(status = status) }
        persistIfEnabled()
    }

    private fun persistIfEnabled() {
        if (!_state.value.saveFilters) return
        viewModelScope.launch { persistFilters() }
    }

    private suspend fun persistFilters() {
        val s = _state.value
        settingsStore.setTournamentFilters(
            TournamentSavedFilters(zone = s.zone, level = s.level, year = s.year, status = s.status),
        )
    }

    private fun loadInternal(force: Boolean) {
        lastRequestedAt = nowMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), refreshing = force, error = null) }
            try {
                val list = repository.tournamentList(force)
                _state.update { it.copy(loading = false, refreshing = false, error = null, items = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "赛事列表加载失败", e)
                _state.update { it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试") }
            }
        }
    }
}
