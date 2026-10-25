package com.rzh.valo.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.epochToLocalDate
import com.rzh.valo.data.filterBySeries
import com.rzh.valo.data.formatCnDate
import com.rzh.valo.data.nowMillis
import com.rzh.valo.data.plusDays
import com.rzh.valo.data.startOfDayMillis
import com.rzh.valo.data.todayCn
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** 状态筛选：0 全部 · 1 未开始 · 3 已结束（进行中在「全部」中可见） */
object ScheduleFilter {
    const val ALL = 0
    const val SCHEDULED = 1
    const val FINISHED = 3
}

data class DayGroup(
    val date: LocalDate,
    val isToday: Boolean,
    val matches: List<MatchItem>,
)

data class ScheduleUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val days: List<DayGroup> = emptyList(),
    val totalCount: Int = 0,
    /** 相对默认窗口偏移的周数 */
    val weekOffset: Int = 0,
    /** 日期选择器选定的自定义区间（含两端）；非空时优先于周平移 */
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val filter: Int = ScheduleFilter.ALL,
) {
    val isDefaultWindow: Boolean get() = weekOffset == 0 && customStart == null

    /** 自定义区间下周平移无意义 */
    val canShiftWeek: Boolean get() = customStart == null

    val windowLabel: String
        get() {
            if (customStart != null) {
                val end = customEnd ?: customStart
                return if (end == customStart) {
                    formatCnDate(customStart)
                } else {
                    "${formatCnDate(customStart)} – ${formatCnDate(end)}"
                }
            }
            val today = todayCn()
            val start = today.plusDays(weekOffset * 7 - ScheduleViewModel.DEFAULT_PAST_DAYS)
            val end = today.plusDays(ScheduleViewModel.DEFAULT_FUTURE_DAYS + weekOffset * 7)
            return "${formatCnDate(start)} – ${formatCnDate(end)}"
        }
}

class ScheduleViewModel(
    private val repository: MatchRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state = _state.asStateFlow()

    private var items: List<MatchItem> = emptyList()
    private var hiddenSeries: Set<String> = emptySet()
    private var seriesMap: Map<String, String> = emptyMap()
    private var loadJob: Job? = null
    /** 上次发起联网加载的时间，作为回前台是否再验证的依据 */
    private var lastRequestedAt = 0L
    /** 屏蔽设置与系列映射就绪前不渲染，避免冷启动先闪出未过滤的赛程 */
    private var filterReady = false

    init {
        viewModelScope.launch {
            settingsStore.scheduleHiddenSeries.collectLatest { hidden ->
                hiddenSeries = hidden
                if (filterReady) recompute() else prepareFilter()
            }
        }
    }

    private suspend fun prepareFilter() {
        val cachedMap = runCatching { repository.cachedSeriesMap() }.getOrNull()
        if (cachedMap == null) {
            // 首次安装没有映射快照：等联网结果（失败则为空映射，过滤自动降级）
            seriesMap = runCatching { repository.tournamentSeriesMap() }.getOrDefault(emptyMap())
        } else {
            seriesMap = cachedMap
            fetchSeriesMap()
        }
        filterReady = true
        loadCached()
    }

    /** 下拉刷新：强制绕过接口层缓存并显示下拉指示器 */
    fun refresh() = loadInternal(force = true)

    /** 回到前台：距上次发起加载超过接口层缓存 TTL 时静默再验证，覆盖进程存活的热启动 */
    fun onResume() {
        if (!filterReady) return
        if (nowMillis() - lastRequestedAt > MatchRepository.WINDOW_TTL) {
            loadInternal(force = false)
        }
    }

    fun setFilter(filter: Int) {
        _state.update { it.copy(filter = filter) }
        recompute()
    }

    /** 打开页面先画快照保证秒开，随后总是静默联网再验证；接口层 TTL 会把短时间内的重复请求去重 */
    private fun loadCached() {
        lastRequestedAt = nowMillis()
        viewModelScope.launch {
            val (start, end) = currentWindow(
                _state.value.weekOffset,
                _state.value.customStart,
                _state.value.customEnd,
            )
            val cached = repository.cachedSchedule(start, end)
            if (cached.isNotEmpty()) {
                items = cached
                recompute()
                _state.update { it.copy(loading = false, error = null) }
            }
            loadInternal(force = false)
        }
    }

    /** 联网拉取；[force] 为 true 表示用户手动下拉（绕过接口层缓存并显示下拉指示器） */
    private fun loadInternal(force: Boolean) {
        lastRequestedAt = nowMillis()
        loadJob?.cancel()
        fetchSeriesMap()
        val requestedOffset = _state.value.weekOffset
        val requestedCustomStart = _state.value.customStart
        val requestedCustomEnd = _state.value.customEnd
        val isDefaultWindow = requestedOffset == 0 && requestedCustomStart == null
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.days.isEmpty(), refreshing = force, error = null) }
            try {
                val (start, end) = currentWindow(requestedOffset, requestedCustomStart, requestedCustomEnd)
                val list = repository.schedule(start, end, force)
                if (_state.value.weekOffset != requestedOffset ||
                    _state.value.customStart != requestedCustomStart ||
                    _state.value.customEnd != requestedCustomEnd
                ) {
                    return@launch
                }
                items = list
                // 仅默认窗口写快照，小组件始终消费"近期"数据
                if (isDefaultWindow) repository.saveSnapshot(list, start, end)
                recompute()
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "赛程加载失败", e)
                _state.update { it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试") }
            }
        }
    }

    fun shiftWeek(delta: Int) {
        if (!_state.value.canShiftWeek) return
        items = emptyList()
        _state.update {
            it.copy(weekOffset = it.weekOffset + delta, days = emptyList(), totalCount = 0)
        }
        loadInternal(force = false)
    }

    fun backToDefault() {
        if (_state.value.isDefaultWindow) return
        items = emptyList()
        _state.update {
            it.copy(weekOffset = 0, customStart = null, customEnd = null, days = emptyList(), totalCount = 0)
        }
        loadCached()
    }

    /** 日期选择器确认：查询 [start, end]（含两端）内的比赛；单日则 start == end */
    fun setCustomRange(start: LocalDate, end: LocalDate) {
        items = emptyList()
        _state.update {
            it.copy(customStart = start, customEnd = end, weekOffset = 0, days = emptyList(), totalCount = 0)
        }
        loadInternal(force = false)
    }

    private fun recompute() {
        val today = todayCn()
        val seriesFiltered = items.filterBySeries(seriesMap, hiddenSeries)
        val filtered = when (val f = _state.value.filter) {
            ScheduleFilter.SCHEDULED -> seriesFiltered.filter { it.status == f }
            ScheduleFilter.FINISHED -> seriesFiltered.filter { it.status == f }
            else -> seriesFiltered
        }
        val days = filtered
            .groupBy { epochToLocalDate(it.startTime) }
            .map { (date, matches) -> DayGroup(date, date == today, matches) }
            .sortedBy { it.date }
        _state.update {
            it.copy(
                days = days,
                totalCount = filtered.size,
            )
        }
    }

    /** 系列赛屏蔽映射（tournament_id → 系列赛），失败时为空映射、过滤自动失效 */
    private fun fetchSeriesMap() {
        viewModelScope.launch {
            seriesMap = runCatching { repository.tournamentSeriesMap() }.getOrDefault(emptyMap())
            recompute()
        }
    }

    /** 默认窗口：过去 7 天 + 未来 14 天；左右箭头按周平移。自定义区间优先（含两端，end 换算为次日零点的左闭右开）。 */
    private fun currentWindow(
        weekOffset: Int,
        customStart: LocalDate?,
        customEnd: LocalDate?,
    ): Pair<Long, Long> {
        if (customStart != null) {
            val end = (customEnd ?: customStart).plusDays(1)
            return customStart.startOfDayMillis() to end.startOfDayMillis()
        }
        val today = todayCn()
        val start = today.plusDays(weekOffset * 7 - DEFAULT_PAST_DAYS).startOfDayMillis()
        val end = today.plusDays(DEFAULT_FUTURE_DAYS + weekOffset * 7).startOfDayMillis()
        return start to end
    }

    companion object {
        const val DEFAULT_PAST_DAYS = 7
        const val DEFAULT_FUTURE_DAYS = 14
    }
}
