package com.rzh.valo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.FightHistoryData
import com.rzh.valo.data.MapRecord
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.data.RoundData
import com.rzh.valo.data.VersusInfo
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchDetailUiState(
    val loading: Boolean = true,
    val item: MatchItem? = null,
    /** 已开赛/完赛时加载的地图小局明细；未开赛为空对象 */
    val round: RoundData = RoundData(),
    /** 未开赛时的赛前前瞻（历史交手 + 地图胜率）；未开赛以外为 null */
    val foresight: ForesightUiState? = null,
    val error: String? = null,
    /** 下拉刷新进行中（不遮挡已有内容） */
    val refreshing: Boolean = false,
) {
    val hasMaps: Boolean get() = round.list.isNotEmpty()
}

/** 赛前前瞻：H2H 交手记录与双方分地图历史战绩 */
data class ForesightUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val fight: FightHistoryData? = null,
    val maps: List<MapRecord> = emptyList(),
)

class MatchDetailViewModel(
    private val repository: MatchRepository,
    private val matchId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchDetailUiState())
    val state = _state.asStateFlow()

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
                if (item.status == MatchStatus.SCHEDULED) loadForesight(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "比赛详情刷新失败 matchId=$matchId", e)
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
                if (item.status == MatchStatus.SCHEDULED) loadForesight(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "比赛详情加载失败 matchId=$matchId", e)
                _state.update { it.copy(loading = false, error = "加载失败，请重试") }
            }
        }
    }

    /** 未开赛时并发拉取历史交手与地图胜率；单项失败不阻塞另一项 */
    private fun loadForesight(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(foresight = ForesightUiState(loading = true)) }
            try {
                val fight = async { runCatching { repository.fightHistory(matchId, force) }.getOrNull() }
                val maps = async {
                    runCatching { repository.mapForesight(matchId, force) }.getOrDefault(emptyList())
                }
                val fightData = fight.await()
                val mapData = maps.await()
                _state.update {
                    it.copy(
                        foresight = ForesightUiState(
                            loading = false,
                            failed = fightData == null && mapData.isEmpty(),
                            fight = fightData,
                            maps = mapData,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "赛前前瞻加载失败 matchId=$matchId", e)
                _state.update { it.copy(foresight = ForesightUiState(loading = false, failed = true)) }
            }
        }
    }

    /**
     * 详情与逐回合并发拉取（round 接口对未开赛比赛返回空数据，失败不阻塞详情展示）；
     * 未开赛时丢弃 round 结果，与"开赛后可查看"的占位文案一致。
     */
    private suspend fun fetchDetail(force: Boolean): Pair<MatchItem, RoundData> = coroutineScope {
        val round = async {
            runCatching { repository.matchRound(matchId, force) }.getOrDefault(RoundData())
        }
        val item = repository.matchDetail(matchId, force)
        val roundData = round.await()
        item to if (item.status == MatchStatus.SCHEDULED) RoundData() else roundData
    }
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
