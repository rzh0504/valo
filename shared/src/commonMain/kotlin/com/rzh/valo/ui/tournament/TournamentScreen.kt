package com.rzh.valo.ui.tournament

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.IntegralRow
import com.rzh.valo.data.formatCnDateWithWeekday
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.OnResumeEffect
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.team.TeamMatchGroup
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun TournamentScreen(
    tournamentId: String,
    tournamentName: String,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: TournamentViewModel = viewModel(key = "tournament_$tournamentId") {
        TournamentViewModel(container.repository, tournamentId, tournamentName)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnResumeEffect { viewModel.onResume() }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = state.tournamentName,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingPane(label = "正在获取赛事信息") }

            state.error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorPane(state.error!!, viewModel::retry)
            }

            else -> {
                PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    TournamentList(state, viewModel, onOpenMatch)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TournamentList(
    state: TournamentUiState,
    viewModel: TournamentViewModel,
    onOpenMatch: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
    ) {
        item(key = "stages") { StageTabs(state, viewModel) }

        // 积分榜（仅选中具体阶段且该阶段有积分数据时展示）
        if (state.selectedStageId != null) {
            when {
                state.integralLoading && state.integralGroups.isEmpty() -> item(key = "integral_loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        InfiniteProgressIndicator()
                    }
                }

                state.integralGroups.isNotEmpty() -> {
                    item(key = "integral_title") {
                        SectionTitle("积分榜")
                    }
                    state.integralGroups.forEach { group ->
                        if (group.groupName != null && !group.groupName.startsWith("默认")) {
                            item(key = "gn_${group.groupName}") {
                                Text(
                                    group.groupName,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        item(key = "it_${group.groupName}") { IntegralTable(group.rows) }
                    }
                }
            }
        }

        // 赛程
        item(key = "match_title") { SectionTitle("赛程") }
        if (state.groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.selectedStageId == null) "该赛事暂无比赛" else "该阶段暂无比赛",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        } else {
            state.groups.forEach { group ->
                item(key = "day_${group.date}") { DateHeader(group) }
                items(group.matches, key = { it.id }) { match ->
                    MatchCard(
                        item = match,
                        onClick = { onOpenMatch(match.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StageTabs(state: TournamentUiState, viewModel: TournamentViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        item(key = "all") {
            FilterChip(
                label = "全部",
                selected = state.selectedStageId == null,
                onClick = { viewModel.selectStage(null) },
            )
        }
        items(state.stages, key = { it.id }) { stage ->
            FilterChip(
                label = stage.name.orEmpty(),
                selected = state.selectedStageId == stage.id,
                onClick = { viewModel.selectStage(stage.id) },
            )
        }
    }
}

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

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MiuixTheme.textStyles.headline2,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 阶段积分榜：名次 / 队伍 / 胜 / 负 / 回合净胜 */
@Composable
private fun IntegralTable(rows: List<IntegralRow>) {
    val scheme = MiuixTheme.colorScheme
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                Spacer(Modifier.width(50.dp))
                Text(
                    "队伍",
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "胜",
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    "负",
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    "回合",
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(44.dp),
                )
            }
            rows.forEachIndexed { index, row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = scheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TeamLogo(row.team?.icon, size = 22)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.team?.displayName ?: "待定",
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${row.win}",
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        "${row.lose}",
                        style = MiuixTheme.textStyles.body2,
                        color = scheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        row.roundDiff.let { if (it > 0) "+$it" else "$it" },
                        style = MiuixTheme.textStyles.body2,
                        color = if (row.roundDiff > 0) {
                            scheme.primary
                        } else {
                            scheme.onSurfaceVariantSummary
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(group: TeamMatchGroup) {
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
            formatCnDateWithWeekday(group.date),
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
