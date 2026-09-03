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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.ui.components.MatchCard
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onOpenMatch: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: ScheduleViewModel = viewModel { ScheduleViewModel(app.container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var scrolledToToday by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.days) {
        if (!scrolledToToday && state.days.isNotEmpty()) {
            val index = state.days.indexOfFirst { it.isToday }
            val target = if (index >= 0) index else {
                // 窗口内没有今天的比赛时，落到第一个不早于今天的分组
                state.days.indexOfFirst { !it.date.isBefore(java.time.LocalDate.now()) }
            }
            if (target > 0) {
                listState.scrollToItem(headerOffset(state, target))
            }
            scrolledToToday = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("无畏契约赛程") },
                actions = {
                    Text(
                        state.windowLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { viewModel.shiftWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    IconButton(onClick = { viewModel.shiftWeek(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(state, viewModel)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null && state.days.isEmpty() -> ErrorPane(
                        message = state.error!!,
                        onRetry = { viewModel.refresh() },
                    )
                    state.days.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "该时间段内暂无比赛",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> MatchList(state, listState, onOpenMatch)
                }
            }
        }
    }
}

private fun headerOffset(state: ScheduleUiState, dayIndex: Int): Int =
    state.days.take(dayIndex).sumOf { it.matches.size + 1 }

@Composable
private fun FilterRow(state: ScheduleUiState, viewModel: ScheduleViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(
            selected = state.filter == ScheduleFilter.ALL,
            onClick = { viewModel.setFilter(ScheduleFilter.ALL) },
            label = { Text("全部") },
        )
        FilterChip(
            selected = state.filter == ScheduleFilter.SCHEDULED,
            onClick = { viewModel.setFilter(ScheduleFilter.SCHEDULED) },
            label = { Text("未开始") },
        )
        FilterChip(
            selected = state.filter == ScheduleFilter.FINISHED,
            onClick = { viewModel.setFilter(ScheduleFilter.FINISHED) },
            label = { Text("已结束") },
        )
        Spacer(Modifier.weight(1f))
        if (!state.isDefaultWindow) {
            TextButton(onClick = { viewModel.backToDefault() }) { Text("回到近期") }
        }
    }
}

@Composable
private fun MatchList(state: ScheduleUiState, listState: LazyListState, onOpenMatch: (String) -> Unit) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.days.forEach { day ->
            item(key = "header_${day.date}") {
                val fmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (day.isToday) "今天" else day.date.format(fmt),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
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
        item(key = "footer") {
            Text(
                "共 ${state.totalCount} 场 · 数据来自号角 haojiao.cc",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
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
