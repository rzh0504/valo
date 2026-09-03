package com.rzh.valo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.MatchStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val date: LocalDate = LocalDate.now(),
    val live: List<MatchItem> = emptyList(),
    val scheduled: List<MatchItem> = emptyList(),
    val finished: List<MatchItem> = emptyList(),
    /** 当前筛选的比赛状态；null 表示全部 */
    val filter: Int? = null,
) {
    val total: Int get() = live.size + scheduled.size + finished.size
    val hasMatches: Boolean get() = total > 0
}

class HomeViewModel(private val repository: MatchRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load(force = true)

    /** 点击汇总胶囊切换筛选；再次点击取消筛选回到全部 */
    fun toggleFilter(status: Int) {
        _state.update { it.copy(filter = if (it.filter == status) null else status) }
    }

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !it.hasMatches, refreshing = force, error = null) }
            try {
                val today = LocalDate.now(CN_ZONE)
                val start = today.atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(CN_ZONE).toInstant().toEpochMilli()
                val items = repository.schedule(start, end, force)
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        date = today,
                        live = items.filter { m -> m.status == MatchStatus.LIVE }.sortedBy { m -> m.startTime },
                        scheduled = items.filter { m -> m.status == MatchStatus.SCHEDULED }.sortedBy { m -> m.startTime },
                        finished = items.filter { m -> m.status == MatchStatus.FINISHED }.sortedByDescending { m -> m.startTime },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = "网络请求失败，请下拉重试") }
            }
        }
    }
}
