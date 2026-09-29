package com.rzh.valo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.AppContainer
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val container: AppContainer,
    /** 设置变更后由平台回调刷新桌面小组件（Android 实现，其他平台为空） */
    private val onWidgetDataChanged: (suspend () -> Unit)? = null,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        container.settingsStore.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val matchLevels: StateFlow<Set<String>> =
        container.settingsStore.matchLevels.stateIn(viewModelScope, SharingStarted.Eagerly, MATCH_LEVELS.toSet())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.settingsStore.setThemeMode(mode)
            onWidgetDataChanged?.invoke()
        }
    }

    fun setMatchLevel(level: String, enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setMatchLevel(level, enabled)
            onWidgetDataChanged?.invoke()
        }
    }
}
