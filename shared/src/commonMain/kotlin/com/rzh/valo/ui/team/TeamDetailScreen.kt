package com.rzh.valo.ui.team

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.RosterMember
import com.rzh.valo.data.TeamBase
import com.rzh.valo.data.TeamMapStats
import com.rzh.valo.data.TeamReward
import com.rzh.valo.data.epochToLocalDate
import com.rzh.valo.data.formatCnDate
import com.rzh.valo.data.formatTime
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.Route
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.SectionCard
import com.rzh.valo.ui.components.StatsTableCard
import com.rzh.valo.ui.components.StatsTableCell
import com.rzh.valo.ui.components.StatsTableHeaderRow
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.components.tableZebraColor
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 战队详情：基础信息、近期比赛、地图胜率、赛事名次与选手名单，近期比赛行可点进比赛详情 */
@Composable
fun TeamDetailScreen(
    route: Route.TeamInfo,
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenRewards: () -> Unit,
    onOpenPlayer: (String, String, String) -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: TeamDetailViewModel = viewModel(key = "teaminfo_${route.teamId}") {
        TeamDetailViewModel(
            container.repository,
            route.teamId,
            route.name,
            route.short,
            route.icon.takeIf { it.isNotBlank() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = state.base?.displayName?.ifBlank { route.name }?.ifBlank { "战队详情" } ?: route.name.ifBlank { "战队详情" },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefresh(
            isRefreshing = false,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingPane(
                    modifier = Modifier.fillMaxSize(),
                    label = "正在获取战队数据",
                )
                state.failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Text("战队数据加载失败", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(text = "重试", onClick = viewModel::retry)
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    TeamHeaderCard(state.base, route)
                    Spacer(Modifier.height(12.dp))
                    if (state.detailsLoading) {
                        DetailsSkeleton()
                    } else {
                        RecentMatchesSection(
                            matches = state.recentMatches,
                            onOpenMatch = onOpenMatch,
                            onOpenSchedule = onOpenSchedule,
                        )
                        state.stats?.let { stats ->
                            val maps = stats.mapStats.filter { it.games > 0 }.sortedByDescending { it.games }
                            if (maps.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                TeamMapCard(maps)
                            }
                        }
                        if (state.rewards.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            TeamRewardCard(state.rewards, onOpenRewards)
                        }
                        state.roster?.let { roster ->
                            if (roster.active.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                TeamRosterCard(roster.active, onOpenPlayer)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 近期比赛：斑马表格列最近 5 场（比分按战队视角，胜场强调），标题行右侧"全部赛程"快捷跳转 */
@Composable
private fun RecentMatchesSection(
    matches: List<RecentMatchRow>,
    onOpenMatch: (String) -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    StatsTableCard(title = "近期比赛", action = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onOpenSchedule)
                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                "全部赛程",
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "进入全部赛程",
                tint = scheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }) {
        if (matches.isEmpty()) {
            Text(
                "暂无近期比赛",
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            )
        } else {
            StatsTableHeaderRow("对阵", listOf("比分" to 64.dp))
            matches.forEachIndexed { index, match ->
                val meta = listOfNotNull(
                    match.eventLabel.takeIf { it.isNotBlank() },
                    formatCnDate(epochToLocalDate(match.startTime)) + " " + formatTime(match.startTime),
                ).joinToString(" · ")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tableZebraColor(index, scheme))
                        .clickable { onOpenMatch(match.matchId) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        TeamLogo(match.opponent?.icon, size = 20)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                match.opponent?.displayName ?: "待定",
                                style = MiuixTheme.textStyles.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                meta,
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    StatsTableCell(
                        "${match.ownScore} : ${match.oppScore}",
                        64.dp,
                        emphasized = match.won == true,
                    )
                }
            }
        }
    }
}

/** 区块加载骨架：两张表格形状的灰色脉冲占位，数据就位后整批替换，避免内容闪现 */
@Composable
private fun DetailsSkeleton() {
    val transition = rememberInfiniteTransition(label = "teamDetailSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "teamDetailPulse",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.alpha(pulse),
    ) {
        SkeletonTable(rows = 6)
        SkeletonTable(rows = 3)
    }
}

@Composable
private fun SkeletonTable(rows: Int) {
    val scheme = MiuixTheme.colorScheme
    val block = scheme.surfaceContainerHighest
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(rows) {
                Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(block))
            }
        }
    }
}

/** 头部：徽标居中，下方一行赛区 · 地点 · 成立时间（不展示简介文本） */
@Composable
private fun TeamHeaderCard(base: TeamBase?, route: Route.TeamInfo) {
    val scheme = MiuixTheme.colorScheme
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TeamLogo(base?.icon ?: route.icon.takeIf { it.isNotBlank() }, size = 72, shape = CircleShape)
            val meta = listOfNotNull(
                base?.zoneName,
                base?.city,
                base?.establishDate,
            ).filterNotNull().filter { it.isNotBlank() }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    meta.joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 逐图胜率（仅近期打过的图，按场次降序） */
@Composable
private fun TeamMapCard(maps: List<TeamMapStats>) {
    val scheme = MiuixTheme.colorScheme
    StatsTableCard(title = "地图胜率") {
        StatsTableHeaderRow("地图", listOf("场次" to 56.dp, "胜率" to 56.dp))
        maps.forEachIndexed { index, map ->
            val rate = if (map.games > 0) map.wins * 100.0 / map.games else 0.0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(tableZebraColor(index, scheme)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                ) {
                    TeamLogo(map.icon, size = 20)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        map.displayName,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatsTableCell("${map.games}局", 56.dp, secondary = true)
                StatsTableCell("${rate.roundToInt()}%", 56.dp, emphasized = rate >= 50)
            }
        }
    }
}

/** 赛事名次与奖金（最近 5 条），标题右侧可进入全部名次 */
@Composable
private fun TeamRewardCard(rewards: List<TeamReward>, onOpenRewards: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    StatsTableCard(
        title = "赛事名次",
        action = if (rewards.size > REWARD_PREVIEW_COUNT) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = onOpenRewards)
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        "查看全部",
                        style = MiuixTheme.textStyles.footnote1,
                        color = scheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "查看全部赛事名次",
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            null
        },
    ) {
        RewardTableHeader()
        rewards.take(REWARD_PREVIEW_COUNT).forEachIndexed { index, reward ->
            RewardRow(reward, index)
        }
    }
}

private const val REWARD_PREVIEW_COUNT = 5

/** 选手名单：现役（替补/不活跃带标注），选手行可进入选手详情 */
@Composable
private fun TeamRosterCard(
    active: List<RosterMember>,
    onOpenPlayer: (String, String, String) -> Unit,
) {
    SectionCard(title = "选手名单") {
        active.forEachIndexed { index, member ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            RosterRow(member, onOpenPlayer)
        }
    }
}

@Composable
private fun RosterRow(member: RosterMember, onOpenPlayer: (String, String, String) -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val person = member.person
    val career = member.career
    val playerId = person?.id?.takeIf { it.isNotBlank() && career?.type == 1 }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (playerId != null) {
                    Modifier.clickable {
                        onOpenPlayer(
                            playerId,
                            career?.idName?.takeIf { it.isNotBlank() } ?: person?.name.orEmpty(),
                            person?.icon.orEmpty(),
                        )
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        TeamLogo(person?.icon, size = 36, shape = RoundedCornerShape(50))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                member.nationalityIcon?.let {
                    TeamLogo(it, size = 14)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    career?.idName.orEmpty(),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = when {
                    member.isInactive -> " · 不活跃"
                    member.isSub -> " · 替补"
                    else -> ""
                }
                if (tag.isNotEmpty()) {
                    Text(
                        tag,
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
            Text(
                listOfNotNull(
                    person?.name?.takeIf { it.isNotBlank() },
                    career?.roleLabel?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        member.joinDate?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
            )
        }
        if (playerId != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "进入选手详情",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
