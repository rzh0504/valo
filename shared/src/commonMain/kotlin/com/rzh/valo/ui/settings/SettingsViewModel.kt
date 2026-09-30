package com.rzh.valo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.AppContainer
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.data.UpdateChecker
import com.rzh.valo.data.isNewerVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 检查更新的结果状态 */
sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    /** 发现新版本：version 不含 v 前缀，url 为 Release 页面 */
    data class Available(val version: String, val url: String) : UpdateCheck
    data object Failed : UpdateCheck
}

class SettingsViewModel(
    private val container: AppContainer,
    /** 设置变更后由平台回调刷新桌面小组件（Android 实现，其他平台为空） */
    private val onWidgetDataChanged: (suspend () -> Unit)? = null,
    /** 当前版本号（LocalVersionName），用于比较 Release 标签 */
    private val currentVersion: String = "",
) : ViewModel() {

    private val updateChecker = UpdateChecker()

    val themeMode: StateFlow<ThemeMode> =
        container.settingsStore.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val matchLevels: StateFlow<Set<String>> =
        container.settingsStore.matchLevels.stateIn(viewModelScope, SharingStarted.Eagerly, MATCH_LEVELS.toSet())

    val blurEffect: StateFlow<Boolean> =
        container.settingsStore.blurEffect.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _updateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val updateCheck: StateFlow<UpdateCheck> = _updateCheck.asStateFlow()

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

    fun setBlurEffect(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setBlurEffect(enabled)
        }
    }

    /** 手动触发检查；已是最新/失败后可再次触发 */
    fun checkUpdate() {
        if (_updateCheck.value == UpdateCheck.Checking) return
        viewModelScope.launch {
            _updateCheck.value = UpdateCheck.Checking
            _updateCheck.value = try {
                val release = updateChecker.latestRelease()
                if (isNewerVersion(currentVersion, release.version)) {
                    UpdateCheck.Available(release.version, release.url)
                } else {
                    UpdateCheck.UpToDate
                }
            } catch (_: Exception) {
                UpdateCheck.Failed
            }
        }
    }
}
