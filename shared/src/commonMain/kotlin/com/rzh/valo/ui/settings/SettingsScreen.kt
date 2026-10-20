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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.NavBarStyle
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

/** 导航栏样式下拉选项（顺序与 NavBarStyle.entries 一致） */
private val NAV_BAR_LABELS = NavBarStyle.entries.map { it.label }

/** 设置页入口的摘要：未屏蔽给结论，屏蔽时给数量 */
private fun seriesSummary(hiddenSeries: Set<String>): String =
    if (hiddenSeries.isEmpty()) "全部系列显示" else "已隐藏 ${hiddenSeries.size} 个系列"

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
    // 悬浮底栏高度：让列表末项能滚出栏顶（默认样式为 0，由外层 pager 垫高）
    extraBottomPadding: Dp = 0.dp,
) {
    val container = LocalAppContainer.current
    val versionName = LocalVersionName.current
    val widgetRefresher = LocalWidgetRefresher.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val viewModel: SettingsViewModel = viewModel(key = "settings") {
        SettingsViewModel(container, widgetRefresher, currentVersion = versionName)
    }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val navigationBarStyle by viewModel.navigationBarStyle.collectAsStateWithLifecycle()
    val hiddenSeries by viewModel.hiddenSeries.collectAsStateWithLifecycle()
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp + extraBottomPadding),
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
                    OverlayDropdownPreference(
                        items = NAV_BAR_LABELS,
                        selectedIndex = navigationBarStyle.id,
                        title = "导航栏样式",
                        summary = "底部导航栏使用常规或悬浮样式",
                        onSelectedIndexChange = { viewModel.setNavigationBarStyle(NavBarStyle.from(it)) },
                    )
                }
            }
            item(key = "filter") {
                SmallTitle(text = "赛程过滤")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "管理过滤项",
                        summary = seriesSummary(hiddenSeries),
                        onClick = onOpenScheduleFilter,
                    )
                }
            }
            item(key = "app") {
                SmallTitle(text = "应用")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "关于",
                        summary = versionName.ifBlank { "1.0.10" },
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
