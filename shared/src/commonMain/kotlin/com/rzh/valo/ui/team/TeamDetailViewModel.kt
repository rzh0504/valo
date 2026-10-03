package com.rzh.valo.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.data.PlayerStatsRow
import com.rzh.valo.data.TeamBase
import com.rzh.valo.data.TeamReward
import com.rzh.valo.data.TeamRoster
import com.rzh.valo.data.TeamStats
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 详情页近期比赛行：仅已结束场次，比分按目标战队视角展示（自身分在前） */
data class RecentMatchRow(
    val matchId: String,
    val opponent: Participant?,
    val ownScore: String,
    val oppScore: String,
    /** null = 胜负数据缺失 */
    val won: Boolean?,
    val startTime: Long,
    val eventLabel: String,
)

data class TeamDetailUiState(
    val loading: Boolean = true,
    /** 基础信息失败 = 整页失败（显示重试）；其余区块失败只隐藏对应区块 */
    val failed: Boolean = false,
    val base: TeamBase? = null,
    /** 基础信息之外的首批区块是否仍在加载（真：显示骨架屏，避免数据闪现） */
    val detailsLoading: Boolean = true,
    val roster: TeamRoster? = null,
    val stats: TeamStats? = null,
    val playerStats: List<PlayerStatsRow> = emptyList(),
    val rewards: List<TeamReward> = emptyList(),
    val recentMatches: List<RecentMatchRow> = emptyList(),
)

/**
 * 战队详情：基础信息 / 名单 / 战队数据 / 选手数据 / 赛事名次 / 近期比赛。
 * 基础信息成败决定整页成败，其余数据各自独立加载，失败不互相阻塞。
 */
class TeamDetailViewModel(
    private val repository: MatchRepository,
    private val teamId: String,
    private val teamName: String = "",
    private val teamShort: String = "",
    private val teamIcon: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamDetailUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load(force = true)

    /** 下拉刷新：全部区块强制重拉，单项失败保留旧数据 */
    fun refresh() {
        viewModelScope.launch {
            try {
                loadAll(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "战队详情刷新失败 teamId=$teamId", e)
            }
        }
    }

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            try {
                val base = repository.teamBase(teamId, force)
                _state.update { it.copy(loading = false, base = base, detailsLoading = true) }
                loadAll(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "战队详情加载失败 teamId=$teamId", e)
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** 基础信息之外的数据并发拉取，单项失败不阻塞其余 */
    private suspend fun loadAll(force: Boolean) = coroutineScope {
        val roster = async { runCatching { repository.teamRoster(teamId, force) }.getOrNull() }
        val stats = async { runCatching { repository.teamStats(teamId, force) }.getOrNull() }
        val playerStats = async {
            runCatching { repository.playerTeamStats(teamId, force) }.getOrDefault(emptyList())
        }
        val rewards = async {
            runCatching { repository.teamReward(teamId, force) }.getOrDefault(emptyList())
        }
        val recent = async {
            runCatching { fetchRecentMatches(force) }.getOrDefault(emptyList())
        }
        _state.update {
            it.copy(
                detailsLoading = false,
                roster = roster.await() ?: it.roster,
                stats = stats.await() ?: it.stats,
                playerStats = playerStats.await().ifEmpty { it.playerStats },
                rewards = rewards.await().ifEmpty { it.rewards },
                recentMatches = recent.await().ifEmpty { it.recentMatches },
            )
        }
    }

    /**
     * 战队最近 [RECENT_COUNT] 场已结束的比赛：接口没有按战队过滤的参数，
     * 整站倒序分页按战队过滤，凑满即停。分页缓存与战队赛程页共享，
     * 后续点进完整赛程不产生额外请求。
     */
    private suspend fun fetchRecentMatches(force: Boolean): List<RecentMatchRow> {
        val rows = ArrayList<RecentMatchRow>()
        var page = 1
        while (page <= RECENT_MAX_PAGES && rows.size < RECENT_COUNT) {
            val data = repository.recentMatchesPage(page, force)
            for (item in data.list) {
                if (item.status != MatchStatus.FINISHED) continue
                val side = sideOf(item)
                if (side == 0) continue
                rows += toRecentRow(item, side)
                if (rows.size >= RECENT_COUNT) break
            }
            if (data.list.size < MatchRepository.PAGE_SIZE) break
            page++
        }
        return rows
    }

    private fun toRecentRow(item: MatchItem, side: Int): RecentMatchRow {
        val versus = item.versus
        val opponent = if (side == 1) versus?.guestCamp?.firstOrNull() else versus?.mainCamp?.firstOrNull()
        val won = when {
            versus?.isMainWin == 1 -> side == 1
            versus?.isMainWin == 2 -> side == 2
            else -> null
        }
        val ownScore = if (side == 1) versus?.mainScore else versus?.guestScore
        val oppScore = if (side == 1) versus?.guestScore else versus?.mainScore
        return RecentMatchRow(
            matchId = item.id,
            opponent = opponent,
            ownScore = ownScore ?: "0",
            oppScore = oppScore ?: "0",
            won = won,
            startTime = item.startTime,
            eventLabel = item.group?.nameMain?.takeIf { it.isNotBlank() }
                ?: item.tournament?.nameMain?.takeIf { it.isNotBlank() }
                ?: item.tournament?.nameSub.orEmpty(),
        )
    }

    /** 目标战队在该比赛中的阵营：1 主队 · 2 客队 · 0 不在场（与战队赛程页同一套匹配规则） */
    private fun sideOf(item: MatchItem): Int {
        val versus = item.versus ?: return 0
        if (versus.mainCamp?.firstOrNull()?.let(::sameTeam) == true) return 1
        if (versus.guestCamp?.firstOrNull()?.let(::sameTeam) == true) return 2
        return 0
    }

    private fun sameTeam(a: Participant): Boolean {
        val base = _state.value.base
        if (a.id != null && a.id == teamId) return true
        if (a.icon != null && listOfNotNull(teamIcon, base?.icon).any { it == a.icon }) return true
        val names = setOfNotNull(a.nameMain, a.nameShort)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
        val targets = setOfNotNull(
            teamName.takeIf { it.isNotBlank() },
            teamShort.takeIf { it.isNotBlank() },
            base?.displayName?.takeIf { it.isNotBlank() },
        ).map { it.lowercase() }.toSet()
        return names.isNotEmpty() && names.intersect(targets).isNotEmpty()
    }

    companion object {
        /** 详情页展示的近期比赛场数 */
        private const val RECENT_COUNT = 5
        /** 凑满近期比赛的整站分页上限（每页 100 场） */
        private const val RECENT_MAX_PAGES = 5
    }
}
