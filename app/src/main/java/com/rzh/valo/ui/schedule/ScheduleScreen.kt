package com.rzh.valo.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.SkeletonCards
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScheduleScreen(onOpenMatch: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: ScheduleViewModel = viewModel {
        ScheduleViewModel(app.container.repository, app.container.settingsStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var scrolledToToday by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        state = pullState,
        // Expressive 变形加载指示器：拉动时随距离变形，刷新中持续动画
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullState,
                isRefreshing = state.refreshing,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            ScheduleTopBlock(state, viewModel, listState, onPickDate = { showDatePicker = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonCards(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp),
                    )
                    state.error != null && state.days.isEmpty() -> ErrorPane(
                        message = state.error!!,
                        onRetry = { viewModel.refresh() },
                    )
                    else -> ScheduleList(state, listState, onOpenMatch)
                }
                TodayFab(
                    state = state,
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        DateRangeDialog(
            initialStart = state.customStart,
            initialEnd = state.customEnd,
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** 目标日期头在 LazyColumn 中的 index：头部已移出列表，每天占「日期头 + 当日场次」项 */
private fun headerOffset(state: ScheduleUiState, dayIndex: Int): Int =
    state.days.take(dayIndex).sumOf { it.matches.size + 1 }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleList(
    state: ScheduleUiState,
    listState: LazyListState,
    onOpenMatch: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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
            stickyHeader(key = "day_${day.date}") { DayHeader(day) }
            items(day.matches, key = { it.id }) { match ->
                MatchCard(
                    item = match,
                    onClick = { onOpenMatch(match.id) },
                    // 筛选/刷新时平滑位移与淡入淡出，避免列表硬切
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    val fmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
    ) {
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

/** 滚离今天后显示的悬浮按钮：点击回到今天的日期头 */
@Composable
private fun TodayFab(
    state: ScheduleUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val todayIndex = remember(state.days) {
        state.days.indexOfFirst { it.isToday }.takeIf { it >= 0 }
    }
    val visible by remember(state.days, todayIndex) {
        derivedStateOf {
            todayIndex != null &&
                listState.layoutInfo.visibleItemsInfo.none { it.key == "day_${state.days[todayIndex].date}" }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        ExtendedFloatingActionButton(
            onClick = {
                todayIndex?.let { scope.launch { listState.animateScrollToItem(headerOffset(state, it)) } }
            },
            icon = { Icon(Icons.Rounded.Today, contentDescription = null) },
            text = { Text("回到今天") },
        )
    }
}

/** 顶部区块：大标题与时间窗行在列表滚动时收起，筛选行常驻 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTopBlock(
    state: ScheduleUiState,
    viewModel: ScheduleViewModel,
    listState: LazyListState,
    onPickDate: () -> Unit,
) {
    val expanded by remember { derivedStateOf { !listState.canScrollBackward } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    "赛程",
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { viewModel.shiftWeek(-1) }, enabled = state.canShiftWeek) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    // 点击时间窗文字（带日历角标）打开日期范围选择
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onPickDate)
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            state.windowLabel,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Event,
                            contentDescription = "选择日期",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.shiftWeek(1) }, enabled = state.canShiftWeek) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周")
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        ) {
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

/** 日期范围选择：按官方文档样式，DatePickerDialog 内嵌 DateRangePicker；只选一天即查单日 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // state 工厂可指定 locale，日历的月份/星期文案跟随（remember 版只能用系统语言）
    val pickerState = remember {
        DateRangePickerState(
            locale = Locale.CHINA,
            initialSelectedStartDateMillis = initialStart?.toPickerMillis(),
            initialSelectedEndDateMillis = initialEnd?.toPickerMillis(),
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = pickerState.selectedStartDateMillis ?: return@TextButton
                    val end = pickerState.selectedEndDateMillis ?: start
                    onConfirm(start.toPickerLocalDate(), end.toPickerLocalDate())
                },
                enabled = pickerState.selectedStartDateMillis != null,
            ) { Text("查询") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DateRangePicker(
            state = pickerState,
            showModeToggle = false,
            // 官方默认 title/headline 为英文文案，替换为中文
            title = {
                Text(
                    "选择日期范围",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            },
            headline = {
                val start = pickerState.selectedStartDateMillis?.toPickerLocalDate()
                val end = pickerState.selectedEndDateMillis?.toPickerLocalDate()
                val fmt = DateTimeFormatter.ofPattern("M月d日")
                Text(
                    when {
                        start == null -> "开始日期 – 结束日期"
                        end == null || start == end -> start.format(fmt)
                        else -> "${start.format(fmt)} – ${end.format(fmt)}"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (start == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            },
            modifier = Modifier.fillMaxWidth().height(500.dp),
        )
    }
}

/** DatePicker 的毫秒值按 UTC 零点换算，与选中的日历格子对齐，避免时区差出一天 */
private fun Long.toPickerLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
