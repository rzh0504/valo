package com.rzh.valo.ui.schedule

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.components.DateRangePickerDialog
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.OnResumeEffect
import com.rzh.valo.ui.components.SkeletonCards
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ScheduleScreen(onOpenMatch: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ScheduleViewModel = viewModel(key = "schedule") {
        ScheduleViewModel(container.repository, container.settingsStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 回到前台时静默再验证，进程存活的热启动不再滞留旧数据
    OnResumeEffect { viewModel.onResume() }
    val listState = rememberLazyListState()
    var scrolledToToday by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.days) {
        if (!scrolledToToday && state.days.isNotEmpty()) {
            val index = state.days.indexOfFirst { it.isToday }
            val target = if (index >= 0) index else {
                state.days.indexOfFirst { !it.date.isBeforeToday() }
            }
            if (target > 0) {
                listState.scrollToItem(headerOffset(state, target))
            }
            scrolledToToday = true
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "赛程",
                largeTitle = state.windowLabel,
                scrollBehavior = scrollBehavior,
                bottomContent = {
                    ScheduleFilterRow(state, viewModel, onPickDate = { showDatePicker = true })
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                else -> PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    ScheduleList(state, listState, onOpenMatch)
                }
            }
            TodayFab(
                state = state,
                listState = listState,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
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

private fun kotlinx.datetime.LocalDate.isBeforeToday(): Boolean = this < com.rzh.valo.data.todayCn()

/** 目标日期头在 LazyColumn 中的 index：每天占「日期头 + 当日场次」项 */
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
    ) {
        if (state.days.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "该时间段内暂无比赛",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    val scheme = MiuixTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            if (day.isToday) "今天" else com.rzh.valo.data.formatCnDateWithWeekday(day.date),
            style = MiuixTheme.textStyles.body1,
            color = scheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (day.isToday) {
            Spacer(Modifier.width(6.dp))
            Text(
                com.rzh.valo.data.formatCnDateWithWeekday(day.date),
                style = MiuixTheme.textStyles.body1,
                color = scheme.onSurfaceVariantSummary,
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
        FloatingActionButton(
            onClick = {
                todayIndex?.let { scope.launch { listState.animateScrollToItem(headerOffset(state, it)) } }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                Icon(Icons.Rounded.Today, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
                Text("回到今天", color = MiuixTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** 常驻筛选行：周平移 + 时间窗（点击打开日期选择） + 状态分段 */
@Composable
private fun ScheduleFilterRow(
    state: ScheduleUiState,
    viewModel: ScheduleViewModel,
    onPickDate: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.shiftWeek(-1) }, enabled = state.canShiftWeek) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周")
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPickDate)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    state.windowLabel,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Event,
                    contentDescription = "选择日期",
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { viewModel.shiftWeek(1) }, enabled = state.canShiftWeek) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周")
            }
        }
        if (!state.isDefaultWindow) {
            Text(
                "自定义区间 · 点击回到近期",
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.backToDefault() }
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        FilterSegments(
            options = listOf(
                ScheduleFilter.ALL to "全部",
                ScheduleFilter.SCHEDULED to "未开始",
                ScheduleFilter.FINISHED to "已结束",
            ),
            selected = state.filter,
            onSelect = { viewModel.setFilter(it) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
    }
}

/** 状态分段选择（miuix TabRow 风格胶囊） */
@Composable
internal fun FilterSegments(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    top.yukonga.miuix.kmp.basic.TabRow(
        tabs = options.map { it.second },
        selectedTabIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onTabSelected = { index -> onSelect(options[index].first) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(message, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}
