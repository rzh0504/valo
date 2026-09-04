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
    val filter: Int = ScheduleFilter.ALL,
) {
    val isDefaultWindow: Boolean get() = weekOffset == 0
    val windowLabel: String
        get() {
            val today = LocalDate.now()
            val start = today.plusDays(weekOffset * 7L - ScheduleViewModel.DEFAULT_PAST_DAYS)
            val end = today.plusDays(ScheduleViewModel.DEFAULT_FUTURE_DAYS.toLong() + weekOffset * 7L)
            val fmt = java.time.format.DateTimeFormatter.ofPattern("M月d日")
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
        _state.update { it.copy(weekOffset = it.weekOffset + delta) }
        load()
    }

    fun backToDefault() {
        if (_state.value.isDefaultWindow) return
        _state.update { it.copy(weekOffset = 0) }
        load()
    }

    fun refresh() = load(force = true)

    fun setFilter(filter: Int) {
        _state.update { it.copy(filter = filter) }
        recompute()
    }

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.days.isEmpty(), refreshing = force, error = null) }
            try {
                val (start, end) = currentWindow()
                val list = repository.schedule(start, end, force)
                items = list
                // 仅默认窗口写快照，小组件始终消费"近期"数据
                if (_state.value.isDefaultWindow) repository.saveSnapshot(list)
                recompute()
            } catch (e: Exception) {
                recompute(error = "网络请求失败，请下拉重试")
            }
        }
    }

    private fun recompute(error: String? = null) {
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
                loading = false,
                refreshing = false,
                error = error,
                days = days,
                totalCount = filtered.size,
            )
        }
    }

    /** 默认窗口：过去 7 天 + 未来 14 天；左右箭头按周平移。 */
    private fun currentWindow(): Pair<Long, Long> {
        val today = ZonedDateTime.now(CN_ZONE).toLocalDate()
        val start = today.plusDays(_state.value.weekOffset * 7L - DEFAULT_PAST_DAYS)
            .atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
        val end = today.plusDays(DEFAULT_FUTURE_DAYS.toLong() + _state.value.weekOffset * 7L)
            .atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
        return start to end
    }

    companion object {
        const val DEFAULT_PAST_DAYS = 7
        const val DEFAULT_FUTURE_DAYS = 14
    }
}
