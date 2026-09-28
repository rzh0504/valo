package com.rzh.valo.ui.team

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.PlayerStatsRow
import com.rzh.valo.data.TeamBase
import com.rzh.valo.data.TeamRecord
import com.rzh.valo.data.TeamReward
import com.rzh.valo.data.TeamRoster
import com.rzh.valo.data.TeamStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamDetailUiState(
    val loading: Boolean = true,
    /** 基础信息失败 = 整页失败（显示重试）；其余区块失败只隐藏对应区块 */
    val failed: Boolean = false,
    val base: TeamBase? = null,
    val roster: TeamRoster? = null,
    val stats: TeamStats? = null,
    val record: TeamRecord? = null,
    val playerStats: List<PlayerStatsRow> = emptyList(),
    val rewards: List<TeamReward> = emptyList(),
)

/**
 * 战队详情：基础信息 / 名单 / 战队数据 / 选手数据 / 赛事名次。
 * 基础信息成败决定整页成败，其余数据各自独立加载，失败不互相阻塞。
 */
class TeamDetailViewModel(
    private val repository: MatchRepository,
    private val teamId: String,
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
                Log.w("valo", "战队详情刷新失败 teamId=$teamId", e)
            }
        }
    }

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            try {
                val base = repository.teamBase(teamId, force)
                _state.update { it.copy(loading = false, base = base) }
                loadAll(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("valo", "战队详情加载失败 teamId=$teamId", e)
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** 基础信息之外的数据并发拉取，单项失败不阻塞其余 */
    private suspend fun loadAll(force: Boolean) = coroutineScope {
        val roster = async { runCatching { repository.teamRoster(teamId, force) }.getOrNull() }
        val stats = async { runCatching { repository.teamStats(teamId, force) }.getOrNull() }
        val record = async { runCatching { repository.teamRecord(teamId, force) }.getOrNull() }
        val playerStats = async {
            runCatching { repository.playerTeamStats(teamId, force) }.getOrDefault(emptyList())
        }
        val rewards = async {
            runCatching { repository.teamReward(teamId, force) }.getOrDefault(emptyList())
        }
        _state.update {
            it.copy(
                roster = roster.await() ?: it.roster,
                stats = stats.await() ?: it.stats,
                record = record.await() ?: it.record,
                playerStats = playerStats.await().ifEmpty { it.playerStats },
                rewards = rewards.await().ifEmpty { it.rewards },
            )
        }
    }
}
