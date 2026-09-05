package com.rzh.valo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.data.RecentBigMatchData
import com.rzh.valo.data.RoundData
import com.rzh.valo.data.TeamRecentSummary
import com.rzh.valo.data.VersusInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchDetailUiState(
    val loading: Boolean = true,
    val item: MatchItem? = null,
    /** 已开赛/完赛时加载的地图小局明细；未开赛为空对象 */
    val round: RoundData = RoundData(),
    val error: String? = null,
    /** 下拉刷新进行中（不遮挡已有内容） */
    val refreshing: Boolean = false,
) {
    val hasMaps: Boolean get() = round.list.isNotEmpty()
}

/** 战队近期大赛表现：胜负统计 + 已结束/进行中的比赛（时间倒序），未开始的不展示 */
data class TeamRecentUiState(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val summary: TeamRecentSummary? = null,
    val matches: List<MatchItem> = emptyList(),
)

class MatchDetailViewModel(
    private val repository: MatchRepository,
    private val matchId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchDetailUiState())
    val state = _state.asStateFlow()

    private val _teamRecent = MutableStateFlow<TeamRecentUiState?>(null)
    val teamRecent = _teamRecent.asStateFlow()

    /** foresight 数据按比赛 ID 缓存一份，主/客两队各取各的（side → 状态），再次打开 sheet 秒出 */
    private var recentData: RecentBigMatchData? = null
    private val teamRecentBySide = mutableMapOf<Int, TeamRecentUiState>()
    private var teamRecentJob: Job? = null

    init {
        load()
    }

    fun retry() = load(force = true)

    /** 下拉刷新：强制拉取最新数据；失败时保留现有内容静默结束，可再次下拉重试 */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val (item, round) = fetchDetail(force = true)
                _state.update { it.copy(refreshing = false, item = item, round = round, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val (item, round) = fetchDetail(force)
                _state.update { it.copy(loading = false, item = item, round = round) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "加载失败，请重试") }
            }
        }
    }

    private suspend fun fetchDetail(force: Boolean): Pair<MatchItem, RoundData> {
        val item = repository.matchDetail(matchId, force)
        val round = if (item.status != MatchStatus.SCHEDULED) {
            runCatching { repository.matchRound(matchId, force) }.getOrDefault(RoundData())
        } else {
            RoundData()
        }
        return item to round
    }

    /** 打开战队近期战绩 sheet 时加载； foresight 一次请求含双队数据，按点击的队伍取对应一侧 */
    fun loadTeamRecent(team: Participant) {
        teamRecentJob?.cancel()
        teamRecentJob = viewModelScope.launch {
            val side = teamSide(_state.value.item?.versus, team)
            teamRecentBySide[side]?.let {
                _teamRecent.value = it
                return@launch
            }
            _teamRecent.value = TeamRecentUiState(loading = true)
            try {
                val data = recentData ?: repository.matchRecent(matchId).also { recentData = it }
                val isMain = side != 2
                val ui = TeamRecentUiState(
                    summary = if (isMain) data.mainScoreCount else data.guestScoreCount,
                    matches = (if (isMain) data.mainTeamMatch else data.guestTeamMatch)
                        .filter { it.status != MatchStatus.SCHEDULED }
                        .sortedByDescending { it.startTime },
                )
                teamRecentBySide[side] = ui
                _teamRecent.value = ui
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _teamRecent.value = TeamRecentUiState(failed = true)
            }
        }
    }

    /** 关闭 sheet：取消在途请求并清空状态，数据缓存保留 */
    fun dismissTeamRecent() {
        teamRecentJob?.cancel()
        _teamRecent.value = null
    }

    companion object
}
/**
 * 队伍在一场对阵中的位置：1 主队 · 2 客队 · 0 不在场。
 * participant_id 为跨场次稳定的战队 ID（与战队库 team_id 一致），队名与队标作兜底。
 */
internal fun teamSide(versus: VersusInfo?, team: Participant): Int {
    if (versus == null) return 0
    fun same(a: Participant): Boolean {
        if (a.id != null && team.id != null && a.id == team.id) return true
        if (a.icon != null && team.icon != null && a.icon == team.icon) return true
        val names = setOfNotNull(a.nameMain, a.nameShort)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
        val targets = setOfNotNull(team.nameMain, team.nameShort)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
        return names.isNotEmpty() && names.intersect(targets).isNotEmpty()
    }
    if (versus.mainCamp?.firstOrNull()?.let(::same) == true) return 1
    if (versus.guestCamp?.firstOrNull()?.let(::same) == true) return 2
    return 0
}
