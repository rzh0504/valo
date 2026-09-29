package com.rzh.valo.ui.team

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.formatCnDate
import com.rzh.valo.data.minusMonths
import com.rzh.valo.data.plusDays
import com.rzh.valo.data.todayCn
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.Route
import com.rzh.valo.ui.components.DateRangePickerDialog
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.OnResumeEffect
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.schedule.FilterSegments
import com.rzh.valo.ui.schedule.ScheduleFilter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun TeamScheduleScreen(
    route: Route.TeamSchedule,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenTournament: (String, String) -> Unit = { _, _ -> },
) {
    val container = LocalAppContainer.current
    val viewModel: TeamScheduleViewModel = viewModel(key = "teamsched_${route.teamId}") {
        TeamScheduleViewModel(container.repository, route.teamId, route.name, route.short, route.icon.takeIf { it.isNotBlank() })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 回到前台时静默再验证，与赛程页保持一致
    OnResumeEffect { viewModel.onResume() }
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

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = state.teamName,
                scrollBehavior = scrollBehavior,
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
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
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
                PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
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
            onPickCustomRange = {
                showFilterSheet = false
                showRangePicker = true
            },
            onDismiss = { showFilterSheet = false },
        )
    }
    if (showRangePicker) {
        DateRangePickerDialog(
            initialStart = state.rangeStart,
            initialEnd = state.rangeEnd,
            onConfirm = { start, end ->
                viewModel.setRange("custom", "${formatCnDate(start)} – ${formatCnDate(end)}", start, end)
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
                label = "全部",
                selected = state.selectedTabId == null,
                onClick = { viewModel.selectTab(null) },
            )
        }
        items(state.tabs, key = { it.tournamentId }) { tab ->
            FilterChip(
                label = tab.label,
                selected = state.selectedTabId == tab.tournamentId,
                onClick = { viewModel.selectTab(tab.tournamentId) },
            )
        }
    }
    val selectedTab = state.tabs.find { it.tournamentId == state.selectedTabId }
    if (selectedTab != null) {
        TextButton(
            text = "查看「${selectedTab.label}」赛事主页",
            onClick = { onOpenTournament(selectedTab.tournamentId, selectedTab.label) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** miuix 风格筛选胶囊 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onClick,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(
            color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        ),
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "footer") { ListFooter(state, viewModel) }
        }
    }
}

/** 统一筛选 sheet：对手搜索 + 比赛状态 + 时间范围（赛事筛选在页面的 tab 行） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: TeamScheduleUiState,
    viewModel: TeamScheduleViewModel,
    onPickCustomRange: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "筛选",
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                label = "搜索对手或赛事",
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text(
                    "比赛状态",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                FilterSegments(
                    options = listOf(
                        ScheduleFilter.ALL to "全部",
                        ScheduleFilter.SCHEDULED to "未开始",
                        ScheduleFilter.FINISHED to "已结束",
                    ),
                    selected = state.statusFilter,
                    onSelect = { viewModel.setStatusFilter(it) },
                )
            }
            Column {
                Text(
                    "时间范围",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = todayCn()
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
                                kotlinx.datetime.LocalDate(today.year, 1, 1),
                                kotlinx.datetime.LocalDate(today.year, 12, 31),
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
                                kotlinx.datetime.LocalDate(y, 1, 1),
                                kotlinx.datetime.LocalDate(y, 12, 31),
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
    FilterChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun GroupHeader(group: TeamMatchGroup) {
    val scheme = MiuixTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (group.isToday) {
            Text(
                "今天",
                style = MiuixTheme.textStyles.body1,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            com.rzh.valo.data.formatCnDateWithWeekday(group.date),
            style = MiuixTheme.textStyles.body1,
            color = if (group.isToday) {
                scheme.onSurfaceVariantSummary
            } else {
                scheme.primary
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ListFooter(state: TeamScheduleUiState, viewModel: TeamScheduleViewModel) {
    when {
        state.loadingMore -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { InfiniteProgressIndicator() }

        // 赛事/范围视图的数据已完整，无需加载更多
        state.rangeStart != null || state.selectedTabId != null -> Unit

        state.endReached -> Text(
            "已经翻到最早的比赛了",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )

        else -> TextButton(
            text = "加载更早的比赛",
            onClick = viewModel::loadMore,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyPane(state: TeamScheduleUiState, viewModel: TeamScheduleViewModel) {
    val scheme = MiuixTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "没有符合条件的比赛",
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
            )
            if (state.hasActiveFilters) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "试试调整筛选条件或搜索关键词",
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
            // 仅默认视图能继续向历史回溯
            if (state.rangeStart == null && state.selectedTabId == null && !state.endReached) {
                Spacer(Modifier.height(12.dp))
                TextButton(text = "加载更早的比赛", onClick = viewModel::loadMore)
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
        Text(message, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}
