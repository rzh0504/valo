package com.rzh.valo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.LocalVersionName
import com.rzh.valo.ui.LocalWidgetRefresher
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 主题模式下拉选项（顺序与 ThemeMode.entries 一致） */
private val THEME_LABELS = ThemeMode.entries.map { it.label }

@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val versionName = LocalVersionName.current
    val widgetRefresher = LocalWidgetRefresher.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val viewModel: SettingsViewModel = viewModel(key = "settings") {
        SettingsViewModel(container, widgetRefresher)
    }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val matchLevels by viewModel.matchLevels.collectAsStateWithLifecycle()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("赛事级别", style = MiuixTheme.textStyles.body1)
                        Spacer(Modifier.weight(1f))
                        MATCH_LEVELS.forEach { level ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { viewModel.setMatchLevel(level, level !in matchLevels) }
                                    .padding(start = 12.dp),
                            ) {
                                Checkbox(
                                    state = ToggleableState(level in matchLevels),
                                    onClick = null,
                                )
                                Text(
                                    level,
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "about") {
                SmallTitle(text = "关于")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = "版本",
                        summary = versionName.ifBlank { "1.0.4" },
                    )
                    ArrowPreference(
                        title = "数据来源",
                        summary = "haojiao.cc · 仅供学习交流使用",
                        onClick = { runCatching { uriHandler.openUri("https://web.haojiao.cc") } },
                    )
                }
            }
        }
    }
}
