package com.rzh.valo.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.LocalWidgetRefresher
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 各赛事级别的描述文案 */
private val LEVEL_SUMMARIES = mapOf(
    "S" to "各赛区一级联赛、全球赛事",
    "A" to "各赛区进化者杯、EWC、邀请赛等",
    "B" to "改变者、全国大赛等",
    "C" to "ENC、赛区预选赛等",
)

/** 赛程过滤独立设置页：按赛事级别勾选赛程中显示的比赛 */
@Composable
fun ScheduleFilterScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val widgetRefresher = LocalWidgetRefresher.current
    // 与设置页共用同名 ViewModel，保证两处读到同一份级别状态
    val viewModel: SettingsViewModel = viewModel(key = "settings") {
        SettingsViewModel(container, widgetRefresher)
    }
    val matchLevels by viewModel.matchLevels.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = "赛程过滤",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
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
            item(key = "levels") {
                SmallTitle(text = "赛事级别")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    MATCH_LEVELS.forEach { level ->
                        CheckboxPreference(
                            title = level,
                            summary = LEVEL_SUMMARIES[level],
                            checked = level in matchLevels,
                            onCheckedChange = { viewModel.setMatchLevel(level, it) },
                            checkboxLocation = CheckboxLocation.End,
                        )
                    }
                }
                Text(
                    "未标注级别的比赛始终显示",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
