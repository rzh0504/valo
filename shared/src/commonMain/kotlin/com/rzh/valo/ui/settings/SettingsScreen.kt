package com.rzh.valo.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.LocalVersionName
import com.rzh.valo.ui.LocalWidgetRefresher
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 主题模式下拉选项（顺序与 ThemeMode.entries 一致） */
private val THEME_LABELS = ThemeMode.entries.map { it.label }

/** 设置页入口的摘要：全选/全不选给结论，部分勾选列出级别 */
private fun levelsSummary(selected: Set<String>): String = when {
    selected.size == MATCH_LEVELS.size -> "全部级别"
    selected.isEmpty() -> "仅显示未标注级别的比赛"
    else -> MATCH_LEVELS.filter { it in selected }.joinToString(" · ")
}

/** 检查更新条目的摘要文案 */
private fun updateSummary(state: UpdateCheck): String? = when (state) {
    UpdateCheck.Idle -> null
    UpdateCheck.Checking -> "正在检查…"
    UpdateCheck.UpToDate -> "已是最新版本"
    is UpdateCheck.Available -> "发现新版本 v${state.version}，点击前往下载"
    UpdateCheck.Failed -> "检查失败，点击重试"
}

@Composable
fun SettingsScreen(
    onOpenScheduleFilter: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val container = LocalAppContainer.current
    val versionName = LocalVersionName.current
    val widgetRefresher = LocalWidgetRefresher.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val viewModel: SettingsViewModel = viewModel(key = "settings") {
        SettingsViewModel(container, widgetRefresher, currentVersion = versionName)
    }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val matchLevels by viewModel.matchLevels.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        // 底部安全区由外层 MainTabs 的 bottomBar 统一处理，内层不再叠加
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "设置",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item(key = "appearance") {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    OverlayDropdownPreference(
                        items = THEME_LABELS,
                        selectedIndex = themeMode.id,
                        title = "主题模式",
                        summary = "选择应用的深浅色跟随方式",
                        onSelectedIndexChange = { viewModel.setThemeMode(ThemeMode.from(it)) },
                    )
                }
            }
            item(key = "filter") {
                SmallTitle(text = "赛程过滤")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "管理过滤项",
                        summary = levelsSummary(matchLevels),
                        onClick = onOpenScheduleFilter,
                    )
                }
            }
            item(key = "app") {
                SmallTitle(text = "应用")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "关于",
                        summary = versionName.ifBlank { "1.0.8" },
                        onClick = onOpenAbout,
                    )
                    ArrowPreference(
                        title = "检查更新",
                        summary = updateSummary(updateCheck),
                        onClick = {
                            when (val u = updateCheck) {
                                is UpdateCheck.Available -> runCatching { uriHandler.openUri(u.url) }
                                else -> viewModel.checkUpdate()
                            }
                        },
                        enabled = updateCheck != UpdateCheck.Checking,
                    )
                }
            }
        }
    }
}
