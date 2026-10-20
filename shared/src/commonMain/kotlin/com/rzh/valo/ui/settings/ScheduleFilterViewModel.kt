package com.rzh.valo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzh.valo.data.AppContainer
import com.rzh.valo.data.DEFAULT_HIDDEN_SERIES
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleFilterViewModel(
    private val container: AppContainer,
    /** 过滤变更后由平台回调刷新桌面小组件（Android 实现，其他平台为空） */
    private val onWidgetDataChanged: (suspend () -> Unit)? = null,
) : ViewModel() {

    /** 赛程中屏蔽的系列赛 id（目录写死，默认屏蔽非热门系列） */
    val hiddenSeries: StateFlow<Set<String>> =
        container.settingsStore.scheduleHiddenSeries.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_HIDDEN_SERIES)

    /** [shown] 为 true 表示显示该系列，否则记录屏蔽 */
    fun setSeriesShown(id: String, shown: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setSeriesHidden(id, !shown)
            onWidgetDataChanged?.invoke()
        }
    }
}
