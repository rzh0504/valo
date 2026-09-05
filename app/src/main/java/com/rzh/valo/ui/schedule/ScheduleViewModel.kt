package com.rzh.valo.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
            val fmt = java.time.format.DateTimeFormatter.ofPattern("M月d日")
            if (customStart != null) {
                val end = customEnd ?: customStart
                return if (end == customStart) {
                    customStart.format(fmt)
                } else {
                    "${customStart.format(fmt)} – ${end.format(fmt)}"
                }
            }
            val today = LocalDate.now(CN_ZONE)
            val start = today.plusDays(weekOffset * 7L - ScheduleViewModel.DEFAULT_PAST_DAYS)
            val end = today.plusDays(ScheduleViewModel.DEFAULT_FUTURE_DAYS.toLong() + weekOffset * 7L)
            return "${start.format(fmt)} – ${end.format(fmt)}"
        }
}

class ScheduleViewModel(
    private val repository: MatchRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state = _state.asStateFlow()

    private var items: List<MatchItem> = emptyList()
    private var matchLevels: Set<String> = MATCH_LEVELS.toSet()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.matchLevels.collectLatest {
                matchLevels = it
                recompute()
            }
        }
        load()
    }

    fun shiftWeek(delta: Int) {
        if (!_state.value.canShiftWeek) return
        items = emptyList()
        _state.update {
            it.copy(weekOffset = it.weekOffset + delta, days = emptyList(), totalCount = 0)
        }
        load()
    }

    fun backToDefault() {
        if (_state.value.isDefaultWindow) return
        items = emptyList()
        _state.update {
            it.copy(weekOffset = 0, customStart = null, customEnd = null, days = emptyList(), totalCount = 0)
        }
        load()
    }

    /** 日期选择器确认：查询 [start, end]（含两端）内的比赛；单日则 start == end */
    fun setCustomRange(start: LocalDate, end: LocalDate) {
        items = emptyList()
        _state.update {
            it.copy(customStart = start, customEnd = end, weekOffset = 0, days = emptyList(), totalCount = 0)
        }
        load()
    }

    fun refresh() = load(force = true)

    fun setFilter(filter: Int) {
        _state.update { it.copy(filter = filter) }
        recompute()
    }

    private fun load(force: Boolean = false) {
        loadJob?.cancel()
        val requestedOffset = _state.value.weekOffset
        val requestedCustomStart = _state.value.customStart
        val requestedCustomEnd = _state.value.customEnd
        val isDefaultWindow = requestedOffset == 0 && requestedCustomStart == null
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.days.isEmpty(), refreshing = force, error = null) }
            try {
                val (start, end) = currentWindow(requestedOffset, requestedCustomStart, requestedCustomEnd)
                if (!force && isDefaultWindow && items.isEmpty()) {
                    repository.cachedSchedule(start, end).takeIf { it.isNotEmpty() }?.let { cached ->
                        items = cached
                        recompute()
                        _state.update { it.copy(loading = false, refreshing = true) }
                    }
                }
                val list = repository.schedule(start, end, force)
                if (_state.value.weekOffset != requestedOffset ||
                    _state.value.customStart != requestedCustomStart ||
                    _state.value.customEnd != requestedCustomEnd
                ) {
                    return@launch
                }
                items = list
                // 仅默认窗口写快照，小组件始终消费"近期"数据
                if (isDefaultWindow) repository.saveSnapshot(list)
                recompute()
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试") }
            }
        }
    }

    private fun recompute() {
        val today = LocalDate.now(CN_ZONE)
        val levelFiltered = items.filter { match ->
            val level = match.level?.uppercase()
            level !in MATCH_LEVELS || level in matchLevels
        }
        val filtered = when (val f = _state.value.filter) {
            ScheduleFilter.SCHEDULED -> levelFiltered.filter { it.status == f }
            ScheduleFilter.FINISHED -> levelFiltered.filter { it.status == f }
            else -> levelFiltered
        }
        val days = filtered
            .groupBy { Instant.ofEpochMilli(it.startTime).atZone(CN_ZONE).toLocalDate() }
            .map { (date, matches) -> DayGroup(date, date == today, matches) }
            .sortedBy { it.date }
        _state.update {
            it.copy(
                days = days,
                totalCount = filtered.size,
            )
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
            return customStart.atStartOfDay(CN_ZONE).toInstant().toEpochMilli() to
                end.atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
        }
        val today = ZonedDateTime.now(CN_ZONE).toLocalDate()
        val start = today.plusDays(weekOffset * 7L - DEFAULT_PAST_DAYS)
            .atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
        val end = today.plusDays(DEFAULT_FUTURE_DAYS.toLong() + weekOffset * 7L)
            .atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
        return start to end
    }

    companion object {
        const val DEFAULT_PAST_DAYS = 7
        const val DEFAULT_FUTURE_DAYS = 14
    }
}
