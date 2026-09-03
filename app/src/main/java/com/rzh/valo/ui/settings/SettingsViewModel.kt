package com.rzh.valo.ui.settings

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.AppContainer
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.widget.ScheduleWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _widgetRefreshing = MutableStateFlow(false)
    val widgetRefreshing: StateFlow<Boolean> = _widgetRefreshing.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setDynamicColor(enabled) }
    }

    /** 立即拉取近期赛程并更新小组件（小组件每小时自动刷新之外的补充手段） */
    fun refreshWidgetNow() {
        if (_widgetRefreshing.value) return
        viewModelScope.launch {
            _widgetRefreshing.value = true
            try {
                withContext(Dispatchers.IO) {
                    val repo = container.repository
                    val (start, end) = repo.widgetWindow()
                    val items = repo.schedule(start, end, force = true)
                    repo.saveSnapshot(items)
                    val manager = GlanceAppWidgetManager(appContext)
                    manager.getGlanceIds(ScheduleWidget::class.java).forEach { id ->
                        ScheduleWidget.update(appContext, id)
                    }
                }
            } catch (e: Exception) {
                // 静默失败：小组件每小时仍会自动重试
            } finally {
                _widgetRefreshing.value = false
            }
        }
    }
}
