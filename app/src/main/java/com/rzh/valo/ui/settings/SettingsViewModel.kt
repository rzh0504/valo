package com.rzh.valo.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.AppContainer
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.widget.updateAllScheduleWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val container: AppContainer,
    private val appContext: Context,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        container.settingsStore.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColor: StateFlow<Boolean> =
        container.settingsStore.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val widgetOpacity: StateFlow<Float> =
        container.settingsStore.widgetOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setDynamicColor(enabled) }
    }

    fun setWidgetOpacity(value: Float) {
        viewModelScope.launch {
            container.settingsStore.setWidgetOpacity(value)
            withContext(Dispatchers.IO) {
                updateAllScheduleWidgets(appContext)
            }
        }
    }
}
