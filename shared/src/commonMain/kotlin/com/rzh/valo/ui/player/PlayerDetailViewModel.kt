package com.rzh.valo.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.PlayerAchievement
import com.rzh.valo.data.PlayerBase
import com.rzh.valo.data.PlayerCareer
import com.rzh.valo.data.PlayerCareerStats
import com.rzh.valo.data.PlayerHeroData
import com.rzh.valo.util.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
    val base: PlayerBase? = null,
    val careers: List<PlayerCareer> = emptyList(),
    val careerStats: PlayerCareerStats? = null,
    val heroData: PlayerHeroData? = null,
    val achievements: List<PlayerAchievement> = emptyList(),
)

/** 选手详情：基础资料决定成败，成绩与生涯各自独立加载 */
class PlayerDetailViewModel(
    private val repository: MatchRepository,
    private val playerId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerDetailUiState())
    val state = _state.asStateFlow()

    init {
        load(force = false)
    }

    fun retry() = load(force = true)

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.base == null,
                    refreshing = force && it.base != null,
                    failed = false,
                )
            }
            try {
                val base = repository.playerBase(playerId, force)
                _state.update { it.copy(base = base, loading = false, failed = false) }
                coroutineScope {
                    val careers = async {
                        runCatching { repository.playerCareer(playerId, force) }.getOrDefault(emptyList())
                    }
                    val careerStats = async {
                        runCatching { repository.playerCareerStats(playerId, force) }.getOrNull()
                    }
                    val heroData = async {
                        runCatching { repository.playerHeroStats(playerId, force) }.getOrNull()
                    }
                    val achievements = async {
                        runCatching { repository.playerAchievements(playerId, force) }.getOrDefault(emptyList())
                    }
                    _state.update {
                        it.copy(
                            careers = careers.await(),
                            careerStats = careerStats.await(),
                            heroData = heroData.await(),
                            achievements = achievements.await(),
                            refreshing = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("valo", "选手详情加载失败 playerId=$playerId", e)
                _state.update {
                    it.copy(loading = false, refreshing = false, failed = it.base == null)
                }
            }
        }
    }
}
