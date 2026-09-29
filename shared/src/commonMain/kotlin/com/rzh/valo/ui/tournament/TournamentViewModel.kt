package com.rzh.valo.ui.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.IntegralGroup
import com.rzh.valo.data.IntegralRow
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.TournamentStage
import com.rzh.valo.data.epochToLocalDate
import com.rzh.valo.data.nowMillis
import com.rzh.valo.data.todayCn
import com.rzh.valo.ui.team.TeamMatchGroup
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 选中阶段的积分榜分组（groupName 来自阶段分组，供表头展示） */
data class IntegralGroupUi(val groupName: String?, val rows: List<IntegralRow>)

data class TournamentUiState(
    val tournamentId: String = "",
    val tournamentName: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val stages: List<TournamentStage> = emptyList(),
    /** 选中的阶段；null = 全部 */
    val selectedStageId: String? = null,
    val integralGroups: List<IntegralGroupUi> = emptyList(),
    val integralLoading: Boolean = false,
    /** 赛事内比赛（按日期分组，新日期在前；随阶段筛选联动） */
    val groups: List<TeamMatchGroup> = emptyList(),
)

/** 赛事主页：阶段筛选 + 阶段积分榜 + 赛事赛程 */
class TournamentViewModel(
    private val repository: MatchRepository,
    private val tournamentId: String,
    tournamentName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TournamentUiState(tournamentId = tournamentId, tournamentName = tournamentName.ifBlank { "赛事" }),
    )
    val state = _state.asStateFlow()

    private var matches: List<MatchItem> = emptyList()
    private val integralByStage = mutableMapOf<String, List<IntegralGroup>>()
    private var lastRequestedAt = 0L

    init {
        initialLoad()
    }

    fun retry() = initialLoad()

    fun refresh() = loadInternal(force = true, showIndicator = true)

    /** 回到前台：距上次请求超过缓存 TTL 时静默再验证 */
    fun onResume() {
        if (nowMillis() - lastRequestedAt <= MatchRepository.WINDOW_TTL) return
        loadInternal(force = false, showIndicator = false)
    }

    fun selectStage(stageId: String?) {
        _state.update { it.copy(selectedStageId = stageId) }
        recompute()
        if (stageId != null && !integralByStage.containsKey(stageId)) {
            fetchIntegral(stageId, force = false)
        }
    }

    private fun initialLoad() {
        loadInternal(force = false, showIndicator = false, fullScreenLoading = true)
    }

    private fun loadInternal(force: Boolean, showIndicator: Boolean, fullScreenLoading: Boolean = false) {
        lastRequestedAt = nowMillis()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = fullScreenLoading && it.groups.isEmpty(),
                    refreshing = showIndicator,
                    error = null,
                )
            }
            try {
                coroutineScope {
                    val stages = async {
                        runCatching { repository.tournamentStages(tournamentId, force) }
                            .getOrDefault(emptyList())
                    }
                    matches = repository.tournamentMatches(tournamentId, force)
                    _state.update { it.copy(stages = stages.await()) }
                }
                // 选中阶段时顺带刷新该阶段积分榜
                val selected = _state.value.selectedStageId
                if (selected != null) {
                    runCatching {
                        integralByStage[selected] = repository.tournamentIntegral(tournamentId, selected, force)
                    }
                }
                recompute()
                _state.update { it.copy(loading = false, refreshing = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "赛事页加载失败 tournamentId=$tournamentId", e)
                _state.update {
                    it.copy(loading = false, refreshing = false, error = if (matches.isEmpty()) "网络请求失败，请重试" else null)
                }
            }
        }
    }

    private fun fetchIntegral(stageId: String, force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(integralLoading = true) }
            try {
                integralByStage[stageId] = repository.tournamentIntegral(tournamentId, stageId, force)
                recompute()
                _state.update { it.copy(integralLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "积分榜加载失败 stageId=$stageId", e)
                _state.update { it.copy(integralLoading = false) }
            }
        }
    }

    private fun recompute() {
        val s = _state.value
        val filtered = if (s.selectedStageId == null) {
            matches
        } else {
            matches.filter { it.stage?.id == s.selectedStageId }
        }
        val today = todayCn()
        val groups = filtered
            .sortedByDescending { it.startTime }
            .groupBy { epochToLocalDate(it.startTime) }
            .map { (date, ms) -> TeamMatchGroup(date, date == today, ms) }

        val integralGroups = s.selectedStageId?.let { stageId ->
            integralByStage[stageId]?.map { group ->
                val groupName = s.stages
                    .flatMap { it.groupList }
                    .find { it.id == group.groupId }
                    ?.name
                IntegralGroupUi(groupName, group.rows)
            }.orEmpty()
        }.orEmpty()

        _state.update { it.copy(groups = groups, integralGroups = integralGroups) }
    }
}
