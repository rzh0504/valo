package com.rzh.valo.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.ui.components.MatchCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onOpenMatch: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: ScheduleViewModel = viewModel {
        ScheduleViewModel(app.container.repository, app.container.settingsStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var scrolledToToday by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.days) {
        if (!scrolledToToday && state.days.isNotEmpty()) {
            val index = state.days.indexOfFirst { it.isToday }
            val target = if (index >= 0) index else {
                state.days.indexOfFirst { !it.date.isBefore(LocalDate.now(CN_ZONE)) }
            }
            if (target > 0) {
                listState.scrollToItem(headerOffset(state, target))
            }
            scrolledToToday = true
        }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingPane(label = "正在整理赛程")
            }
            state.error != null && state.days.isEmpty() -> ErrorPane(
                message = state.error!!,
                onRetry = { viewModel.refresh() },
            )
            else -> ScheduleList(state, listState, viewModel, onOpenMatch)
        }
    }
}

private fun headerOffset(state: ScheduleUiState, dayIndex: Int): Int =
    state.days.take(dayIndex).sumOf { it.matches.size + 1 }

@Composable
private fun ScheduleList(
    state: ScheduleUiState,
    listState: LazyListState,
    viewModel: ScheduleViewModel,
    onOpenMatch: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { ScheduleHeader(state, viewModel) }

        if (state.days.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "该时间段内暂无比赛",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.days.forEach { day ->
            item(key = "header_${day.date}") {
                val fmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (day.isToday) "今天" else day.date.format(fmt),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (day.isToday) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            day.date.format(fmt),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(day.matches, key = { it.id }) { match ->
                MatchCard(item = match, onClick = { onOpenMatch(match.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleHeader(state: ScheduleUiState, viewModel: ScheduleViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
        Text(
            "赛程",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.shiftWeek(-1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周")
            }
            Text(
                state.windowLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { viewModel.shiftWeek(1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                val options = listOf(
                    ScheduleFilter.ALL to "全部",
                    ScheduleFilter.SCHEDULED to "未开始",
                    ScheduleFilter.FINISHED to "已结束",
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.filter == value,
                        onClick = { viewModel.setFilter(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) },
                    )
                }
            }
            if (!state.isDefaultWindow) {
                TextButton(onClick = { viewModel.backToDefault() }) { Text("回到近期") }
            }
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
