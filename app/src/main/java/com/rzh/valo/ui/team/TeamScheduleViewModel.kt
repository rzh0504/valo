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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 赛事 tab：该战队出现过的赛事（按最近一场时间倒序） */
data class TournamentTab(val tournamentId: String, val label: String)

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
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** 默认视图已翻到整站最早一页，没有更早的可加载 */
    val endReached: Boolean = false,
    val groups: List<TeamMatchGroup> = emptyList(),
    val tabs: List<TournamentTab> = emptyList(),
    /** 选中的赛事 tab；null = 全部 */
    val selectedTabId: String? = null,
    val statusFilter: Int = ScheduleFilter.ALL,
    val query: String = "",
    /** 时间范围筛选的档位（null=不限），用于 chip 选中态 */
    val rangePreset: String? = null,
    /** 时间范围展示文案（"近3个月" 或 "M月d日 – M月d日"） */
    val rangeLabel: String? = null,
    /** 时间范围（含两端）；非空时走按时间窗查询，不再按场数回溯 */
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
) {
    val hasActiveFilters: Boolean
        get() = statusFilter != ScheduleFilter.ALL || query.isNotBlank() ||
            selectedTabId != null || rangeStart != null
}

/**
 * 战队完整赛程。接口没有按战队过滤的参数，三种数据源都是"整站拉取 + 客户端按战队过滤"：
 * - 默认视图：整站比赛按开始时间倒序逐页回溯，按场数凑满（首屏 12 场起），加载更早继续翻页；
 * - 赛事 tab：服务端按 tournament_id 拉取该赛事全部比赛（一次到位，无需翻页）；
 * - 时间范围：按 60 天子窗口查询 [start, end] 区间。
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

    /** 默认数据源：已累积的该战队比赛 */
    private var allMatches: List<MatchItem> = emptyList()
    /** 下一个要拉取的整站分页页码（倒序） */
    private var nextPage = 1
    /** 整站分页是否已翻完 */
    private var defaultExhausted = false
    /** 已选赛事的完整比赛（tournament_id 查询结果，已过滤战队） */
    private val tournamentData = mutableMapOf<String, List<MatchItem>>()
    /** 时间范围查询结果（已过滤战队） */
    private var rangeMatches: List<MatchItem> = emptyList()
    private var lastRequestedAt = 0L

    init {
        initialLoad()
    }

    fun retry() {
        val s = _state.value
        when {
            s.rangeStart != null -> fetchRange(force = true)
            s.selectedTabId != null -> fetchTournament(s.selectedTabId!!, force = true)
            else -> initialLoad()
        }
    }

    /** 下拉刷新：重新拉取当前数据源（默认视图只强制刷新最新一页，历史页保持不变） */
    fun refresh() {
        val s = _state.value
        when {
            s.rangeStart != null -> fetchRange(force = true)
            s.selectedTabId != null -> fetchTournament(s.selectedTabId!!, force = true)
            else -> {
                lastRequestedAt = System.currentTimeMillis()
                viewModelScope.launch {
                    _state.update { it.copy(refreshing = true, error = null) }
                    try {
                        appendDefaultPage(1, force = true)
                        recompute()
                        _state.update { it.copy(refreshing = false, loading = false, error = null) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("valo", "战队赛程刷新失败", e)
                        _state.update {
                            it.copy(refreshing = false, loading = false, error = if (allMatches.isEmpty()) "网络请求失败，请下拉重试" else null)
                        }
                    }
                }
            }
        }
    }

    /** 回到前台：距上次请求超过缓存 TTL 时静默再验证 */
    fun onResume() {
        if (System.currentTimeMillis() - lastRequestedAt <= MatchRepository.WINDOW_TTL) return
        lastRequestedAt = System.currentTimeMillis()
        val s = _state.value
        when {
            s.rangeStart != null -> fetchRange(force = false)
            s.selectedTabId != null -> fetchTournament(s.selectedTabId!!, force = false)
            else -> viewModelScope.launch {
                try {
                    appendDefaultPage(1, force = false)
                    recompute()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("valo", "战队赛程再验证失败", e)
                }
            }
        }
    }

    fun selectTab(tournamentId: String?) {
        if (_state.value.selectedTabId == tournamentId) return
        _state.update { it.copy(selectedTabId = tournamentId) }
        if (tournamentId == null || tournamentData.containsKey(tournamentId)) {
            recompute()
            return
        }
        fetchTournament(tournamentId, force = false)
    }

    fun setStatusFilter(filter: Int) {
        _state.update { it.copy(statusFilter = filter) }
        recompute()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        recompute()
    }

    /** 切换时间范围；[start] 为 null 表示不限（回到默认的按场数回溯） */
    fun setRange(preset: String?, label: String?, start: LocalDate?, end: LocalDate?) {
        _state.update {
            it.copy(rangePreset = preset, rangeLabel = label, rangeStart = start, rangeEnd = end)
        }
        if (start == null || end == null) {
            rangeMatches = emptyList()
            recompute()
        } else {
            fetchRange(force = false)
        }
    }

    /** 默认视图向历史继续回溯（仅"全部"tab 下可用；赛事/范围视图数据已完整） */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || defaultExhausted) return
        if (s.rangeStart != null || s.selectedTabId != null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                fetchDefaultPages(allMatches.size + LOAD_MORE_STEP)
                recompute()
                _state.update { it.copy(loadingMore = false, endReached = defaultExhausted) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队历史赛程加载失败", e)
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun initialLoad() {
        lastRequestedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                fetchDefaultPages(INITIAL_TARGET)
                recompute()
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队赛程加载失败", e)
                _state.update {
                    it.copy(loading = false, refreshing = false, error = "网络请求失败，请点击重试")
                }
            }
        }
    }

    /** 按页向历史回溯，直到战队比赛凑满 [targetTotal] 场、单次触发页数用尽或整站翻完 */
    private suspend fun fetchDefaultPages(targetTotal: Int) {
        var pages = 0
        while (!defaultExhausted && pages < PAGES_PER_TRIGGER && allMatches.size < targetTotal) {
            appendDefaultPage(nextPage, force = false)
            pages++
        }
    }

    private suspend fun appendDefaultPage(page: Int, force: Boolean) {
        val data = repository.recentMatchesPage(page, force)
        if (page == nextPage) nextPage++
        allMatches = (allMatches + data.list.filter { sideOf(it) != 0 }).distinctBy { it.id }
        // 最后一页通常不足一页；恰好整页时多翻一次空页兜底
        if (data.list.size < MatchRepository.PAGE_SIZE) defaultExhausted = true
    }

    private fun fetchTournament(tournamentId: String, force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            try {
                val list = repository.tournamentMatches(tournamentId, force)
                    .filter { sideOf(it) != 0 }
                tournamentData[tournamentId] = list
                recompute()
                _state.update { it.copy(loadingMore = false, loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队赛事赛程加载失败", e)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        loading = false,
                        refreshing = false,
                        error = if (allMatches.isEmpty() && rangeMatches.isEmpty()) "网络请求失败，请重试" else null,
                    )
                }
            }
        }
    }

    /** 时间范围查询：拆成 60 天子窗口逐段拉取（每段远小于接口单窗上限，不会截断出缺口） */
    private fun fetchRange(force: Boolean) {
        val start = _state.value.rangeStart ?: return
        val end = _state.value.rangeEnd ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            try {
                val items = ArrayList<MatchItem>()
                var windowStart = start
                var windows = 0
                while (!windowStart.isAfter(end) && windows < MAX_RANGE_WINDOWS) {
                    val windowEnd = minOf(windowStart.plusDays(RANGE_WINDOW_DAYS - 1), end)
                    items += repository.schedule(
                        windowStart.atStartOfDay(CN_ZONE).toInstant().toEpochMilli(),
                        windowEnd.plusDays(1).atStartOfDay(CN_ZONE).toInstant().toEpochMilli(),
                        force,
                    )
                    windowStart = windowEnd.plusDays(1)
                    windows++
                }
                rangeMatches = items.filter { sideOf(it) != 0 }.distinctBy { it.id }
                recompute()
                _state.update { it.copy(loadingMore = false, loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队范围赛程查询失败", e)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        loading = false,
                        refreshing = false,
                        error = if (allMatches.isEmpty() && rangeMatches.isEmpty()) "网络请求失败，请重试" else null,
                    )
                }
            }
        }
    }

    private fun recompute() {
        val s = _state.value
        // tab 列表来自默认累积数据（或范围查询结果），与当前选中态解耦，保证切换 tab 时列表稳定
        val base = if (s.rangeStart != null) rangeMatches else allMatches
        val tabs = base
            .filter { it.tournament?.id != null }
            .groupBy { it.tournament?.id!! }
            .entries
            .sortedByDescending { entry -> entry.value.maxOf { it.startTime } }
            .take(MAX_TABS)
            .map { entry -> TournamentTab(entry.key, tournamentLabel(entry.value.first())) }
        val selectedTabId = s.selectedTabId?.takeIf { id -> tabs.any { it.tournamentId == id } }

        val source = when {
            s.rangeStart != null -> rangeMatches
            selectedTabId != null -> tournamentData[selectedTabId].orEmpty()
            else -> allMatches
        }
        // 范围视图下选赛事：范围内数据已完整，直接客户端过滤；
        // 默认视图下选赛事走 tournamentData 整赛事查询，无需再过滤
        val tabFiltered = if (s.rangeStart != null && selectedTabId != null) {
            source.filter { it.tournament?.id == selectedTabId }
        } else {
            source
        }

        val query = s.query.trim()
        val filtered = tabFiltered.asSequence()
            .filter { m ->
                when (s.statusFilter) {
                    ScheduleFilter.SCHEDULED -> m.status == MatchStatus.SCHEDULED
                    ScheduleFilter.FINISHED -> m.status == MatchStatus.FINISHED
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
        _state.update {
            it.copy(
                groups = groups,
                tabs = tabs,
                selectedTabId = selectedTabId,
                endReached = defaultExhausted,
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

    /** 赛事 tab 展示名：优先赛事分组名（如"CN联赛 第二赛段"、"全球冠军赛"） */
    private fun tournamentLabel(item: MatchItem): String =
        item.group?.nameMain?.takeIf { it.isNotBlank() }
            ?: item.tournament?.nameMain?.takeIf { it.isNotBlank() }
            ?: item.tournament?.nameSub.orEmpty()

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

    companion object {
        /** 首屏至少凑满的场数 */
        private const val INITIAL_TARGET = 12
        /** 每次「加载更早」新增的目标场数 */
        private const val LOAD_MORE_STEP = 12
        /** 单次触发最多翻的整站分页页数（每页 100 场），控制请求频率 */
        private const val PAGES_PER_TRIGGER = 5
        /** 范围查询的子窗口天数与窗口数上限（覆盖"去年"一整年） */
        private const val RANGE_WINDOW_DAYS = 60L
        private const val MAX_RANGE_WINDOWS = 7
        /** 赛事 tab 最多展示个数 */
        private const val MAX_TABS = 8
    }
}
