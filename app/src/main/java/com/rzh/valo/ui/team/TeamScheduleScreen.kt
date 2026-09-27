package com.rzh.valo.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.schedule.ScheduleFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TeamScheduleScreen(
    teamId: String,
    teamName: String,
    teamShort: String,
    teamIcon: String?,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenTournament: (String, String) -> Unit = { _, _ -> },
) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: TeamScheduleViewModel = viewModel {
        TeamScheduleViewModel(app.container.repository, teamId, teamName, teamShort, teamIcon)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 回到前台时静默再验证，与赛程页保持一致
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val listState = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    // 滚动接近列表尾部时自动向历史回溯（仅默认"全部"视图生效）
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }

    // 内容滚到顶栏下方时顶栏变色分层
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamLogo(state.teamIcon, size = 30)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                state.teamName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.teamShort.isNotBlank() && state.teamShort != state.teamName) {
                                Text(
                                    state.teamShort,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = "筛选",
                            tint = if (state.hasActiveFilters) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading || (state.loadingMore && state.groups.isEmpty()) -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingPane(label = "正在获取战队赛程") }

            state.error != null && state.groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorPane(state.error!!, viewModel::retry)
            }

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                TournamentTabs(state, viewModel, onOpenTournament)
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            state = pullState,
                            isRefreshing = state.refreshing,
                        )
                    },
                ) {
                    TeamList(state, listState, viewModel, onOpenMatch)
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            state = state,
            viewModel = viewModel,
            onPickCustomRange = { showRangePicker = true },
            onDismiss = { showFilterSheet = false },
        )
    }
    if (showRangePicker) {
        RangePickerDialog(
            onConfirm = { start, end ->
                val fmt = DateTimeFormatter.ofPattern("M月d日")
                viewModel.setRange(
                    "custom",
                    "${start.format(fmt)} – ${end.format(fmt)}",
                    start,
                    end,
                )
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }
}

/** 赛事 tab：全部 + 该战队出现过的赛事（按最近一场时间倒序）；选中赛事时可进入赛事主页 */
@Composable
private fun TournamentTabs(
    state: TeamScheduleUiState,
    viewModel: TeamScheduleViewModel,
    onOpenTournament: (String, String) -> Unit,
) {
    if (state.tabs.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = state.selectedTabId == null,
                onClick = { viewModel.selectTab(null) },
                label = { Text("全部") },
            )
        }
        items(state.tabs, key = { it.tournamentId }) { tab ->
            FilterChip(
                selected = state.selectedTabId == tab.tournamentId,
                onClick = { viewModel.selectTab(tab.tournamentId) },
                label = {
                    Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
    val selectedTab = state.tabs.find { it.tournamentId == state.selectedTabId }
    if (selectedTab != null) {
        TextButton(
            onClick = { onOpenTournament(selectedTab.tournamentId, selectedTab.label) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("查看「${selectedTab.label}」赛事主页") }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TeamList(
    state: TeamScheduleUiState,
    listState: LazyListState,
    viewModel: TeamScheduleViewModel,
    onOpenMatch: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.groups.isEmpty()) {
            item(key = "empty") { EmptyPane(state, viewModel) }
        } else {
            state.groups.forEach { group ->
                item(key = "day_${group.date}") { GroupHeader(group) }
                items(group.matches, key = { it.id }) { match ->
                    MatchCard(
                        item = match,
                        onClick = { onOpenMatch(match.id) },
                        // 筛选时平滑位移与淡入淡出，避免列表硬切
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "footer") { ListFooter(state, viewModel) }
        }
    }
}

/** 统一筛选 sheet：对手搜索 + 比赛状态 + 时间范围（赛事筛选在页面的 tab 行） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: TeamScheduleUiState,
    viewModel: TeamScheduleViewModel,
    onPickCustomRange: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "筛选",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("搜索对手或赛事") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空")
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text(
                    "比赛状态",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(
                        ScheduleFilter.ALL to "全部",
                        ScheduleFilter.SCHEDULED to "未开始",
                        ScheduleFilter.FINISHED to "已结束",
                    )
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.statusFilter == value,
                            onClick = { viewModel.setStatusFilter(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            label = { Text(label) },
                        )
                    }
                }
            }
            Column {
                Text(
                    "时间范围",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = LocalDate.now(CN_ZONE)
                    RangeChip(
                        label = "不限",
                        selected = state.rangePreset == null,
                        onClick = { viewModel.setRange(null, null, null, null) },
                    )
                    RangeChip(
                        label = "近3个月",
                        selected = state.rangePreset == "3m",
                        onClick = {
                            viewModel.setRange("3m", "近3个月", today.minusMonths(3), today)
                        },
                    )
                    RangeChip(
                        label = "今年",
                        selected = state.rangePreset == "year",
                        onClick = {
                            viewModel.setRange(
                                "year",
                                "今年",
                                LocalDate.of(today.year, 1, 1),
                                LocalDate.of(today.year, 12, 31),
                            )
                        },
                    )
                    RangeChip(
                        label = "去年",
                        selected = state.rangePreset == "last",
                        onClick = {
                            val y = today.year - 1
                            viewModel.setRange(
                                "last",
                                "去年",
                                LocalDate.of(y, 1, 1),
                                LocalDate.of(y, 12, 31),
                            )
                        },
                    )
                    RangeChip(
                        label = "自定义…",
                        selected = state.rangePreset == "custom",
                        onClick = onPickCustomRange,
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** 时间范围选择：只选一天即查单日 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = remember {
        DateRangePickerState(locale = Locale.CHINA)
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
            title = {
                Text(
                    "选择时间范围",
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

@Composable
private fun GroupHeader(group: TeamMatchGroup) {
    val fmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (group.isToday) {
            Text(
                "今天",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            group.date.format(fmt),
            style = MaterialTheme.typography.titleSmall,
            color = if (group.isToday) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListFooter(state: TeamScheduleUiState, viewModel: TeamScheduleViewModel) {
    when {
        state.loadingMore -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { ContainedLoadingIndicator() }

        // 赛事/范围视图的数据已完整，无需加载更多
        state.rangeStart != null || state.selectedTabId != null -> Unit

        state.endReached -> Text(
            "已经翻到最早的比赛了",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )

        else -> TextButton(
            onClick = viewModel::loadMore,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("加载更早的比赛") }
    }
}

@Composable
private fun EmptyPane(state: TeamScheduleUiState, viewModel: TeamScheduleViewModel) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "没有符合条件的比赛",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.hasActiveFilters) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "试试调整筛选条件或搜索关键词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 仅默认视图能继续向历史回溯
            if (state.rangeStart == null && state.selectedTabId == null && !state.endReached) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = viewModel::loadMore) { Text("加载更早的比赛") }
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
