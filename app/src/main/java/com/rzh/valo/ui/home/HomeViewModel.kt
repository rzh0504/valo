package com.rzh.valo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.SettingsStore
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val date: LocalDate = LocalDate.now(CN_ZONE),
    val live: List<MatchItem> = emptyList(),
    val scheduled: List<MatchItem> = emptyList(),
    val finished: List<MatchItem> = emptyList(),
    /** 当前筛选的比赛状态；null 表示全部 */
    val filter: Int? = null,
) {
    val total: Int get() = live.size + scheduled.size + finished.size
    val hasMatches: Boolean get() = total > 0
}

class HomeViewModel(
    private val repository: MatchRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()
    private var items: List<MatchItem> = emptyList()
    private var matchLevels: Set<String> = MATCH_LEVELS.toSet()

    init {
        viewModelScope.launch {
            settingsStore.matchLevels.collectLatest {
                matchLevels = it
                recompute()
            }
        }
        loadCached()
    }

    /** 下拉刷新：唯一的手动联网入口 */
    fun refresh() = loadInternal(force = true)

    /** 点击汇总胶囊切换筛选；再次点击取消筛选回到全部 */
    fun toggleFilter(status: Int) {
        _state.update { it.copy(filter = if (it.filter == status) null else status) }
    }

    /** 打开页面只读快照，不自动联网；快照缺失或过期时才兜底拉取一次 */
    private fun loadCached() {
        viewModelScope.launch {
            val today = LocalDate.now(CN_ZONE)
            val cached = repository.cachedHomeSchedule(
                today.atStartOfDay(CN_ZONE).toInstant().toEpochMilli(),
                today.plusDays(1).atStartOfDay(CN_ZONE).toInstant().toEpochMilli(),
            )
            if (cached.isNotEmpty()) {
                items = cached
                recompute(today)
                _state.update { it.copy(loading = false, error = null) }
            } else {
                loadInternal(force = false)
            }
        }
    }

    /** 联网拉取；[force] 为 true 表示用户手动下拉（绕过接口层缓存并显示下拉指示器） */
    private fun loadInternal(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !it.hasMatches, refreshing = force, error = null) }
            try {
                val today = LocalDate.now(CN_ZONE)
                val start = today.atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
                items = repository.schedule(start, end, force)
                repository.saveHomeSnapshot(items)
                recompute(today)
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试") }
            }
        }
    }

    private fun recompute(date: LocalDate = _state.value.date) {
        val filtered = items.filter { match ->
            val level = match.level?.uppercase()
            level !in MATCH_LEVELS || level in matchLevels
        }
        _state.update {
            it.copy(
                date = date,
                live = filtered.filter { m -> m.status == MatchStatus.LIVE }.sortedBy { m -> m.startTime },
                scheduled = filtered.filter { m -> m.status == MatchStatus.SCHEDULED }.sortedBy { m -> m.startTime },
                finished = filtered.filter { m -> m.status == MatchStatus.FINISHED }.sortedByDescending { m -> m.startTime },
            )
        }
    }
}
