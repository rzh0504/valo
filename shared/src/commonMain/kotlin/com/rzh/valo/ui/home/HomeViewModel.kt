package com.rzh.valo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.filterByLevels
import com.rzh.valo.data.nowMillis
import com.rzh.valo.data.plusDays
import com.rzh.valo.data.startOfDayMillis
import com.rzh.valo.data.todayCn
import com.rzh.valo.util.logWarn
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val date: LocalDate = todayCn(),
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
    /** 上次发起联网加载的时间，作为回前台是否再验证的依据 */
    private var lastRequestedAt = 0L

    init {
        viewModelScope.launch {
            settingsStore.matchLevels.collectLatest {
                matchLevels = it
                recompute()
            }
        }
        loadCached()
    }

    /** 下拉刷新：强制绕过接口层缓存并显示下拉指示器 */
    fun refresh() = loadInternal(force = true)

    /** 回到前台：距上次发起加载超过接口层缓存 TTL 时静默再验证，覆盖进程存活的热启动 */
    fun onResume() {
        if (nowMillis() - lastRequestedAt > MatchRepository.WINDOW_TTL) {
            loadInternal(force = false)
        }
    }

    /** 点击汇总胶囊切换筛选；再次点击取消筛选回到全部 */
    fun toggleFilter(status: Int) {
        _state.update { it.copy(filter = if (it.filter == status) null else status) }
    }

    /** 打开页面先画快照保证秒开，随后总是静默联网再验证；接口层 TTL 会把短时间内的重复请求去重 */
    private fun loadCached() {
        lastRequestedAt = nowMillis()
        viewModelScope.launch {
            val today = todayCn()
            val cached = repository.cachedHomeSchedule(
                today.startOfDayMillis(),
                today.plusDays(1).startOfDayMillis(),
            )
            if (cached.isNotEmpty()) {
                items = cached
                recompute(today)
                _state.update { it.copy(loading = false, error = null) }
            }
            loadInternal(force = false)
        }
    }

    /** 联网拉取；[force] 为 true 表示用户手动下拉（绕过接口层缓存并显示下拉指示器） */
    private fun loadInternal(force: Boolean) {
        lastRequestedAt = nowMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = !it.hasMatches, refreshing = force, error = null) }
            try {
                val today = todayCn()
                val start = today.startOfDayMillis()
                val end = today.plusDays(1).startOfDayMillis()
                items = repository.schedule(start, end, force)
                repository.saveHomeSnapshot(items)
                recompute(today)
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "首页赛程加载失败", e)
                _state.update { it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试") }
            }
        }
    }

    private fun recompute(date: LocalDate = _state.value.date) {
        val filtered = items.filterByLevels(matchLevels)
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
