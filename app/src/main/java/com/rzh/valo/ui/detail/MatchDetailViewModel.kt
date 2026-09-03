package com.rzh.valo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchDetailUiState(
    val loading: Boolean = true,
    val item: MatchItem? = null,
    val error: String? = null,
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

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val item = repository.matchDetail(matchId, force)
                _state.update { it.copy(loading = false, item = item) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "加载失败，请重试") }
            }
        }
    }
}
