package com.rzh.valo.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.TeamReward
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamRewardsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val rewards: List<TeamReward> = emptyList(),
)

/** 战队全部赛事名次；与详情页共用仓库缓存，进入本页通常不产生新请求 */
class TeamRewardsViewModel(
    private val repository: MatchRepository,
    private val teamId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamRewardsUiState())
    val state = _state.asStateFlow()

    init {
        load(force = false)
    }

    fun retry() = load(force = true)

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = it.rewards.isEmpty(), refreshing = force, error = null)
            }
            try {
                val rewards = repository.teamRewards(teamId, force)
                _state.update { it.copy(loading = false, refreshing = false, rewards = rewards) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "赛事名次加载失败 teamId=$teamId", e)
                _state.update {
                    it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试")
                }
            }
        }
    }
}
