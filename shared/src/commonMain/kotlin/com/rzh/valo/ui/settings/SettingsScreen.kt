package com.rzh.valo.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

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
    val widgetOpacity by viewModel.widgetOpacity.collectAsStateWithLifecycle()
    val matchLevels by viewModel.matchLevels.collectAsStateWithLifecycle()
    var opacityDraft by remember(widgetOpacity) { mutableFloatStateOf(widgetOpacity) }
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
                    MATCH_LEVELS.forEach { level ->
                        SwitchPreference(
                            title = "$level 级赛事",
                            summary = if (level == "S") "国际大赛（冠军赛 / 大师赛等）" else null,
                            checked = level in matchLevels,
                            onCheckedChange = { viewModel.setMatchLevel(level, it) },
                        )
                    }
                }
            }
            item(key = "widget") {
                SmallTitle(text = "小组件")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SliderPreference(
                        title = "背景不透明度",
                        value = opacityDraft,
                        onValueChange = { opacityDraft = it },
                        onValueChangeFinished = { viewModel.setWidgetOpacity(opacityDraft) },
                        valueRange = 0.2f..1f,
                        valueText = "${(opacityDraft * 100).roundToInt()}%",
                    )
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
