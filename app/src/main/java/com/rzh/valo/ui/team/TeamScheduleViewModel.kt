package com.rzh.valo.ui.team

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.ui.schedule.ScheduleFilter
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 结果筛选：0 全部 · 1 胜 · 2 负 */
object TeamResultFilter {
    const val ALL = 0
    const val WIN = 1
    const val LOSE = 2
}

/** 按日期分组的一组比赛（新日期在前） */
data class TeamMatchGroup(
    val date: LocalDate,
    val isToday: Boolean,
    val matches: List<MatchItem>,
)

data class TeamScheduleUiState(
    val teamId: String = "",
    val teamName: String = "",
    val teamShort: String = "",
    val teamIcon: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    /** 历史已回溯到下界，没有更早的可加载 */
    val endReached: Boolean = false,
    val groups: List<TeamMatchGroup> = emptyList(),
    /** 已收录的比赛总数与战绩（不受筛选影响） */
    val loadedCount: Int = 0,
    val winCount: Int = 0,
    val loseCount: Int = 0,
    val statusFilter: Int = ScheduleFilter.ALL,
    val resultFilter: Int = TeamResultFilter.ALL,
    val query: String = "",
) {
    val hasActiveFilters: Boolean
        get() = statusFilter != ScheduleFilter.ALL ||
            resultFilter != TeamResultFilter.ALL ||
            query.isNotBlank()
}

/**
 * 战队完整赛程：接口没有按战队过滤的参数，故按 30 天时间段拉取比赛列表、
 * 客户端按战队身份过滤；向过去逐段回溯（加载更早），首屏覆盖近 30 天与未来 60 天。
 */
class TeamScheduleViewModel(
    private val repository: MatchRepository,
    private val teamId: String,
    teamName: String,
    teamShort: String,
    teamIcon: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TeamScheduleUiState(
            teamId = teamId,
            teamName = teamName.ifBlank { "战队赛程" },
            teamShort = teamShort,
            teamIcon = teamIcon,
        ),
    )
    val state = _state.asStateFlow()

    /** 该战队已加载的全部比赛（跨时间段去重） */
    private var items: List<MatchItem> = emptyList()
    /** 已拉取的时间段（按起点升序），刷新时按段再验证 */
    private val loadedSegments = mutableListOf<Pair<Long, Long>>()
    /** 初始窗口起点：比它更早的段属于历史段，刷新时不强制绕过缓存 */
    private var anchorStart = 0L
    private var lastRequestedAt = 0L

    init {
        loadInitial()
    }

    fun retry() = loadInitial()

    /** 下拉刷新：近期段强制拉取，历史段走缓存再验证（已结束的历史不会再变化） */
    fun refresh() {
        if (loadedSegments.isEmpty()) {
            loadInitial()
        } else {
            reloadAll(showIndicator = true)
        }
    }

    /** 回到前台：距上次请求超过缓存 TTL 时静默再验证 */
    fun onResume() {
        if (System.currentTimeMillis() - lastRequestedAt <= MatchRepository.WINDOW_TTL) return
        if (loadedSegments.isEmpty()) loadInitial() else reloadAll(showIndicator = false)
    }

    fun setStatusFilter(filter: Int) {
        _state.update { it.copy(statusFilter = filter) }
        recompute()
    }

    /** 再点一次同结果筛选即取消 */
    fun toggleResult(result: Int) {
        _state.update { it.copy(resultFilter = if (it.resultFilter == result) TeamResultFilter.ALL else result) }
        recompute()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        recompute()
    }

    /** 向过去加载一个时间段；连续 [MAX_SKIP] 段没有该战队比赛时暂停，等下一次触发再继续 */
    fun loadOlder() {
        val s = _state.value
        if (s.loading || s.loadingOlder || s.endReached || s.error != null) return
        val oldest = loadedSegments.minOfOrNull { it.first } ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            try {
                var start = oldest - SEGMENT_MS
                var end = oldest
                var skip = 0
                var found: List<MatchItem> = emptyList()
                var reachedFloor = false
                while (true) {
                    found = repository.schedule(start, end).filter { sideOf(it) != 0 }
                    loadedSegments.add(0, start to end)
                    if (found.isNotEmpty()) break
                    val nextStart = start - SEGMENT_MS
                    if (nextStart < floorStart()) {
                        reachedFloor = true
                        break
                    }
                    if (skip >= MAX_SKIP) break
                    skip++
                    end = start
                    start = nextStart
                }
                if (found.isNotEmpty()) {
                    items = (items + found).distinctBy { it.id }
                    recompute()
                }
                if (reachedFloor) _state.update { it.copy(endReached = true) }
                _state.update { it.copy(loadingOlder = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队历史赛程加载失败", e)
                _state.update { it.copy(loadingOlder = false) }
            }
        }
    }

    /** 首屏：近 30 天 + 未来 60 天，分 3 段并行拉取 */
    private fun loadInitial() {
        lastRequestedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = items.isEmpty(), error = null) }
            try {
                anchorStart = System.currentTimeMillis() - INITIAL_PAST_DAYS * DAY
                val segments = (0 until INITIAL_SEGMENTS).map { i ->
                    val start = anchorStart + i * SEGMENT_MS
                    start to start + SEGMENT_MS
                }
                loadedSegments.clear()
                loadedSegments.addAll(segments)
                items = fetchSegments(segments)
                recompute()
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队赛程加载失败", e)
                _state.update {
                    it.copy(loading = false, refreshing = false, error = if (items.isEmpty()) "网络请求失败，请点击重试" else null)
                }
            }
        }
    }

    private fun reloadAll(showIndicator: Boolean) {
        lastRequestedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(refreshing = showIndicator, error = null) }
            try {
                items = fetchSegments(loadedSegments.toList(), forceRecent = showIndicator)
                recompute()
                _state.update { it.copy(refreshing = false, loading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队赛程刷新失败", e)
                _state.update {
                    it.copy(refreshing = false, loading = false, error = if (items.isEmpty()) "网络请求失败，请下拉重试" else null)
                }
            }
        }
    }

    /** 并行拉取各时间段的比赛并按战队过滤；[forceRecent] 只对近期段强制绕过缓存 */
    private suspend fun fetchSegments(
        segments: List<Pair<Long, Long>>,
        forceRecent: Boolean = false,
    ): List<MatchItem> = coroutineScope {
        segments.map { (start, end) ->
            async { repository.schedule(start, end, force = forceRecent && start >= anchorStart) }
        }.awaitAll()
    }.flatMap { list -> list.filter { sideOf(it) != 0 } }
        .distinctBy { it.id }

    private fun recompute() {
        val s = _state.value
        val query = s.query.trim()
        val filtered = items.asSequence()
            .filter { m ->
                when (s.statusFilter) {
                    ScheduleFilter.SCHEDULED -> m.status == MatchStatus.SCHEDULED
                    ScheduleFilter.FINISHED -> m.status == MatchStatus.FINISHED
                    else -> true
                }
            }
            .filter { m ->
                when (s.resultFilter) {
                    TeamResultFilter.WIN -> isWin(m)
                    TeamResultFilter.LOSE -> m.isFinished && !isWin(m)
                    else -> true
                }
            }
            .filter { m -> query.isEmpty() || searchableText(m).contains(query, ignoreCase = true) }
            .sortedByDescending { it.startTime }
            .toList()
        val today = LocalDate.now(CN_ZONE)
        val groups = filtered
            .groupBy { Instant.ofEpochMilli(it.startTime).atZone(CN_ZONE).toLocalDate() }
            .map { (date, matches) -> TeamMatchGroup(date, date == today, matches) }
        val finished = items.count { it.isFinished && sideOf(it) != 0 }
        val wins = items.count { isWin(it) }
        _state.update {
            it.copy(
                groups = groups,
                loadedCount = items.size,
                winCount = wins,
                loseCount = finished - wins,
            )
        }
    }

    /** 目标战队在该比赛中的阵营：1 主队 · 2 客队 · 0 不在场（与详情页 teamSide 同一套匹配规则） */
    private fun sideOf(item: MatchItem): Int {
        val versus = item.versus ?: return 0
        if (versus.mainCamp?.firstOrNull()?.let(::sameTeam) == true) return 1
        if (versus.guestCamp?.firstOrNull()?.let(::sameTeam) == true) return 2
        return 0
    }

    private fun sameTeam(a: Participant): Boolean {
        val s = _state.value
        if (a.id != null && a.id == s.teamId) return true
        if (a.icon != null && s.teamIcon != null && a.icon == s.teamIcon) return true
        val names = setOfNotNull(a.nameMain, a.nameShort)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
        val targets = setOfNotNull(s.teamName.takeIf { it != "战队赛程" }, s.teamShort)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
        return names.isNotEmpty() && names.intersect(targets).isNotEmpty()
    }

    private fun isWin(item: MatchItem): Boolean {
        val side = sideOf(item)
        return item.isFinished && side != 0 && item.versus?.isMainWin == side
    }

    /** 搜索命中的文本：对手名 + 赛事/阶段名 */
    private fun searchableText(item: MatchItem): String {
        val side = sideOf(item)
        val opponent = (if (side == 2) item.versus?.mainCamp else item.versus?.guestCamp)?.firstOrNull()
        return listOfNotNull(
            opponent?.nameMain,
            opponent?.nameShort,
            item.tournament?.nameMain,
            item.tournament?.nameSub,
            item.group?.nameMain,
            item.stage?.name,
            item.scheduleName,
        ).joinToString(" ")
    }

    private fun floorStart(): Long = anchorStart - MAX_HISTORY_DAYS * DAY

    companion object {
        private const val DAY = 24 * 3600_000L
        /** 回溯步长 30 天：接口单窗上限 100 条 × 3 页，30 天窗口内不会截断出缺口 */
        private const val SEGMENT_MS = 30 * DAY
        private const val INITIAL_PAST_DAYS = 30L
        private const val INITIAL_SEGMENTS = 3
        /** 最多回溯 18 个月 */
        private const val MAX_HISTORY_DAYS = 550L
        /** 向前回溯时，没有该战队比赛的空段自动连续跳过的上限 */
        private const val MAX_SKIP = 2
    }
}
