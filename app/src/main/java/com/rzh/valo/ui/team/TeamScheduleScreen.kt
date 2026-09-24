package com.rzh.valo.ui.team

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.schedule.ScheduleFilter
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
) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: TeamScheduleViewModel = viewModel {
        TeamScheduleViewModel(app.container.repository, teamId, teamName, teamShort, teamIcon)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 回到前台时静默再验证，与赛程页保持一致
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val listState = rememberLazyListState()

    // 滚动接近列表尾部时自动向前加载更早的比赛
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadOlder() }

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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingPane(label = "正在获取战队赛程") }

            state.error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorPane(state.error!!, viewModel::retry)
            }

            else -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding),
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
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "filters") { FilterBlock(state, viewModel) }

        if (state.groups.isEmpty()) {
            item(key = "empty") {
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
                        if (!state.endReached) {
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = viewModel::loadOlder) { Text("加载更早的比赛") }
                        }
                    }
                }
            }
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

@Composable
private fun FilterBlock(state: TeamScheduleUiState, viewModel: TeamScheduleViewModel) {
    Column(Modifier.fillMaxWidth()) {
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
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChip(
                selected = state.resultFilter == TeamResultFilter.WIN,
                onClick = { viewModel.toggleResult(TeamResultFilter.WIN) },
                label = { Text("只看胜") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = state.resultFilter == TeamResultFilter.LOSE,
                onClick = { viewModel.toggleResult(TeamResultFilter.LOSE) },
                label = { Text("只看负") },
            )
            Spacer(Modifier.weight(1f))
            if (state.loadedCount > 0) {
                Text(
                    "已收录 ${state.loadedCount} 场 · ${state.winCount}胜${state.loseCount}负",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
        state.loadingOlder -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { ContainedLoadingIndicator() }

        state.endReached -> Text(
            "已经翻到最早的比赛了",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )

        else -> TextButton(
            onClick = viewModel::loadOlder,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("加载更早的比赛") }
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
