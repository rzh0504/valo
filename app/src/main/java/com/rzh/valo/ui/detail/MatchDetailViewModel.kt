package com.rzh.valo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.RoundData
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
}
