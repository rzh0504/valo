package com.rzh.valo.ui.tournament

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.TournamentItem
import com.rzh.valo.data.TournamentZones
import com.rzh.valo.data.epochToLocalDate
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.components.OnResumeEffect
import com.rzh.valo.ui.components.SkeletonCards
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.components.squircleShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlinx.datetime.LocalDate

/** 主页签「赛事」：全量赛事列表，按年份分组，bottomsheet 筛选，点击进入赛事详情 */
@Composable
fun TournamentListScreen(
    onOpenTournament: (String, String) -> Unit,
    // 悬浮底栏高度：让列表末项能滚出栏顶（默认样式为 0，由外层 pager 垫高）
    extraBottomPadding: Dp = 0.dp,
) {
    val container = LocalAppContainer.current
    val viewModel: TournamentListViewModel = viewModel(key = "tournament_list") {
        TournamentListViewModel(container.repository, container.settingsStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnResumeEffect { viewModel.onResume() }
    var showFilterSheet by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        // 底部安全区由外层 MainTabs 的 bottomBar 统一处理，内层不再叠加
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = "赛事",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "筛选",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
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

                state.error != null && state.items.isEmpty() -> ErrorPane(
                    modifier = Modifier.fillMaxSize(),
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )

                else -> PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    TournamentList(state, onOpenTournament, extraBottomPadding)
                }
            }

            // miuix 弹层依赖根 Scaffold 注册，必须位于 Scaffold content 子树内
            if (showFilterSheet) {
                FilterSheet(state, viewModel, onDismiss = { showFilterSheet = false })
            }
        }
    }
}

/** 筛选 bottomsheet：赛区 / 等级 / 年份 / 状态 + 右上角「保存筛选」（对齐号角网页赛事页，全为客户端过滤） */
@Composable
private fun FilterSheet(
    state: TournamentListUiState,
    viewModel: TournamentListViewModel,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "筛选",
        onDismissRequest = onDismiss,
        endAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "保存筛选",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                // miuix Checkbox 内部 requiredSize(26dp) 写死，只能缩放渲染
                Checkbox(
                    state = ToggleableState(state.saveFilters),
                    onClick = { viewModel.setSaveFilters(!state.saveFilters) },
                    modifier = Modifier.scale(0.75f),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterSection("赛区") {
                items(
                    listOf(
                        TournamentZones.ALL to "全部",
                        TournamentZones.GLOBAL to "全球",
                        TournamentZones.CHINA to "中国",
                        TournamentZones.PACIFIC to "太平洋",
                        TournamentZones.EMEA to "EMEA",
                        TournamentZones.AMERICAS to "美洲",
                    ),
                    key = { it.second },
                ) { (value, label) ->
                    FilterChip(label, state.zone == value) { viewModel.selectZone(value) }
                }
            }
            FilterSection("等级") {
                items(
                    listOf(
                        null to "全部",
                        "S" to "S级",
                        "A" to "A级",
                        "B" to "B级",
                        "C" to "C级",
                    ),
                    key = { it.second },
                ) { (value, label) ->
                    FilterChip(label, state.level == value) { viewModel.selectLevel(value) }
                }
            }
            FilterSection("年份") {
                item(key = "year_all") {
                    FilterChip("全部", state.year == null) { viewModel.selectYear(null) }
                }
                items(state.yearOptions, key = { "year_$it" }) { year ->
                    FilterChip("$year", state.year == year) { viewModel.selectYear(year) }
                }
            }
            FilterSection("状态") {
                items(
                    listOf(
                        0 to "全部",
                        MatchStatus.SCHEDULED to "未开始",
                        MatchStatus.LIVE to "进行中",
                        MatchStatus.FINISHED to "已结束",
                    ),
                    key = { it.second },
                ) { (value, label) ->
                    FilterChip(label, state.status == value) { viewModel.selectStatus(value) }
                }
            }
        }
    }
}

/** 一行筛选：左侧维度标签 + 横向滚动胶囊（单行不换行） */
@Composable
private fun FilterSection(label: String, chips: LazyListScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(36.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier.weight(1f),
            content = chips,
        )
    }
}

/** miuix 风格筛选胶囊（与战队赛程页/赛事详情页同款，紧凑内边距） */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onClick,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TournamentList(
    state: TournamentListUiState,
    onOpenTournament: (String, String) -> Unit,
    extraBottomPadding: Dp,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp + extraBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
    ) {
        val groups = state.groups
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    "暂无赛事",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                )
            }
        }
        groups.forEach { group ->
            stickyHeader(key = "year_${group.year}") { YearHeader(group.year) }
            items(
                items = group.tournaments,
                key = { it.id },
            ) { item ->
                TournamentCard(
                    item = item,
                    onClick = { onOpenTournament(item.id, item.displayName) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun YearHeader(year: Int) {
    Text(
        "${year}年",
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            // 吸顶时遮住下方的卡片，必须是不透明底色
            .background(MiuixTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun TournamentCard(
    item: TournamentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Box(modifier) {
        Card(
            onClick = onClick,
            cornerRadius = 16.dp,
            pressFeedbackType = PressFeedbackType.Sink,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(item.icon, size = 36)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // 完整赛事名（网页 name_full 规则），单行截断
                        Text(
                            item.displayName,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 副标题统一展示赛区（去掉「赛区」后缀）
                        item.zoneName?.let { zone ->
                            Text(
                                zone.removeSuffix("赛区").trim(),
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(item.level)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tournamentDateRange(item),
                        style = MiuixTheme.textStyles.footnote1,
                        color = scheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.weight(1f))
                    // 已结束赛事：右下角奖杯 + 冠军队标 + 队名
                    if (item.isFinished && item.champion != null) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = "冠军",
                            tint = scheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        TeamLogo(item.champion.icon, size = 18)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            item.champion.displayName,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // 进行中赛事：主题色描边高亮（替代状态药丸）
        if (item.isLive) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, scheme.primary, squircleShape(16.dp)),
            )
        }
    }
}

/** 等级徽标（S/A/B/C，数据缺失时不展示） */
@Composable
private fun LevelBadge(level: String?) {
    if (level.isNullOrBlank()) return
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MiuixTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            level,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/** 09/03 短日期（年份由分组头提供） */
private fun shortDate(date: LocalDate): String =
    "${date.monthNumber.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')}"

/** 起止日期展示；数据粗糙时（end 早于 start，如未公布的远期赛事）只展示开始日 */
private fun tournamentDateRange(item: TournamentItem): String {
    if (item.startDate <= 0L) return "时间待定"
    val start = epochToLocalDate(item.startDate)
    if (item.endDate < item.startDate) return shortDate(start)
    val end = epochToLocalDate(item.endDate)
    return when {
        end == start -> shortDate(start)
        end.year == start.year -> "${shortDate(start)} - ${shortDate(end)}"
        else -> "${start.year}/${shortDate(start)} - ${end.year}/${shortDate(end)}"
    }
}

@Composable
private fun ErrorPane(modifier: Modifier = Modifier, message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Text(message, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}
