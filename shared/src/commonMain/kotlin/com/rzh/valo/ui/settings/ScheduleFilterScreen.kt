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
import com.rzh.valo.data.SERIES_CATALOG
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.LocalWidgetRefresher
import com.rzh.valo.ui.components.TeamLogo
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

/** 赛程过滤独立设置页：按系列赛勾选屏蔽，目录写死、热门系列默认显示 */
@Composable
fun ScheduleFilterScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val widgetRefresher = LocalWidgetRefresher.current
    val viewModel: ScheduleFilterViewModel = viewModel(key = "schedule_filter") {
        ScheduleFilterViewModel(container, widgetRefresher)
    }
    val hiddenSeries by viewModel.hiddenSeries.collectAsStateWithLifecycle()
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
            item(key = "series") {
                SmallTitle(text = "系列赛")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SERIES_CATALOG.forEach { series ->
                        CheckboxPreference(
                            title = series.label,
                            startAction = { TeamLogo(series.icon, size = 36) },
                            checked = series.id !in hiddenSeries,
                            onCheckedChange = { viewModel.setSeriesShown(series.id, it) },
                            checkboxLocation = CheckboxLocation.End,
                        )
                    }
                }
                Text(
                    "热门系列默认显示，取消勾选可屏蔽整个系列（如改变者赛）；未收录系列的比赛始终显示",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
