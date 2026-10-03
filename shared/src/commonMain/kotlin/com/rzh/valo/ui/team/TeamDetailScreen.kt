package com.rzh.valo.ui.team

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.PlayerStatsRow
import com.rzh.valo.data.RosterMember
import com.rzh.valo.data.TeamBase
import com.rzh.valo.data.TeamMapStats
import com.rzh.valo.data.TeamRecord
import com.rzh.valo.data.TeamReward
import com.rzh.valo.data.TeamStats
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.formatFixed
import com.rzh.valo.data.percentText
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.Route
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.MatchCard
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

/** 战队详情：基础信息、近期比赛、战队数据、地图胜率、赛事名次与选手名单，近期比赛可点进比赛详情 */
@Composable
fun TeamDetailScreen(
    route: Route.TeamInfo,
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
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
                    RecentMatchesSection(
                        matches = state.recentMatches,
                        onOpenMatch = onOpenMatch,
                        onOpenSchedule = onOpenSchedule,
                    )
                    state.record?.let { record ->
                        state.stats?.let { stats ->
                            Spacer(Modifier.height(12.dp))
                            TeamStatsCard(stats, record)
                        }
                    }
                    state.stats?.let { stats ->
                        val maps = stats.mapStats.filter { it.games > 0 }.sortedByDescending { it.games }
                        if (maps.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            TeamMapCard(maps)
                        }
                    }
                    if (state.rewards.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        TeamRewardCard(state.rewards)
                    }
                    state.roster?.let { roster ->
                        if (roster.active.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            TeamRosterCard(roster.active)
                        }
                    }
                    val activePlayers = state.playerStats
                        .filter { it.isActive && it.rounds > 0 }
                        .sortedByDescending { it.acs }
                    if (activePlayers.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PlayerStatsCard(activePlayers)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 近期比赛：最近 5 场对局卡；标题行右侧带"全部赛程"快捷跳转 */
@Composable
private fun RecentMatchesSection(
    matches: List<MatchItem>,
    onOpenMatch: (String) -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "近期比赛",
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onOpenSchedule)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
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
        }
        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            matches.forEachIndexed { index, match ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                MatchCard(item = match, onClick = { onOpenMatch(match.id) })
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

/** 战队数据：近期（约 2026 年内）与队史两组胜率 */
@Composable
private fun TeamStatsCard(stats: TeamStats, record: TeamRecord) {
    SectionCard(title = "战队数据") {
        Row(Modifier.fillMaxWidth()) {
            StatCell(
                label = "近期胜率",
                value = percentText(stats.matchWinCount, stats.matchCount),
                sub = "${stats.matchWinCount}胜${stats.matchCount - stats.matchWinCount}负",
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = "近期地图",
                value = percentText(stats.gameWinCount, stats.gameCount),
                sub = "${stats.gameWinCount}胜${stats.gameCount - stats.gameWinCount}局",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell(
                label = "队史比赛",
                value = percentText(record.matchWinCount, record.matchCount),
                sub = "${record.matchWinCount}胜${record.matchCount - record.matchWinCount}负",
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = "队史回合",
                value = percentText(record.roundWinCount, record.roundCount),
                sub = "${record.roundWinCount}胜${record.roundCount - record.roundWinCount}回合",
                modifier = Modifier.weight(1f),
            )
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

/** 赛事名次与奖金（最近 5 条）：名次 + 赛事/日期在左，战绩与奖金为对齐列 */
@Composable
private fun TeamRewardCard(rewards: List<TeamReward>) {
    val scheme = MiuixTheme.colorScheme
    StatsTableCard(title = "赛事名次") {
        StatsTableHeaderRow("赛事", listOf("战绩" to 68.dp, "奖金" to 68.dp))
        rewards.take(5).forEachIndexed { index, reward ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tableZebraColor(index, scheme))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    reward.rank.orEmpty(),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(64.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        reward.group?.nameMain.orEmpty(),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        reward.date.orEmpty(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
                StatsTableCell(
                    "${reward.matchWinCount}胜${reward.matchCount - reward.matchWinCount}负",
                    68.dp,
                    secondary = true,
                )
                StatsTableCell(reward.bonus.orEmpty().ifBlank { "—" }, 68.dp, secondary = true)
            }
        }
    }
}

/** 选手名单：现役（替补/不活跃带标注） */
@Composable
private fun TeamRosterCard(active: List<RosterMember>) {
    SectionCard(title = "选手名单") {
        active.forEachIndexed { index, member ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            RosterRow(member)
        }
    }
}

@Composable
private fun RosterRow(member: RosterMember) {
    val scheme = MiuixTheme.colorScheme
    val person = member.person
    val career = member.career
    Row(verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** 选手数据（现役，按 ACS 降序；表头带 + 斑马行） */
@Composable
private fun PlayerStatsCard(players: List<PlayerStatsRow>) {
    val scheme = MiuixTheme.colorScheme
    StatsTableCard(title = "选手数据") {
        StatsTableHeaderRow(
            "选手",
            listOf("ACS" to 52.dp, "KD" to 52.dp, "ADR" to 52.dp, "KAST" to 52.dp),
        )
        players.forEachIndexed { index, player ->
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
                    Text(
                        player.career?.idName.orEmpty(),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    player.heroes.take(3).forEach { hero ->
                        TeamLogo(hero.icon, size = 16)
                        Spacer(Modifier.width(2.dp))
                    }
                }
                StatsTableCell(player.acs.formatFixed(0), 52.dp)
                StatsTableCell(player.kd.formatFixed(2), 52.dp)
                StatsTableCell(player.adr.formatFixed(0), 52.dp)
                StatsTableCell("${player.kast.roundToInt()}%", 52.dp)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Text(
            label,
            style = MiuixTheme.textStyles.footnote1,
            color = scheme.onSurfaceVariantSummary,
        )
        Text(
            sub,
            style = MiuixTheme.textStyles.footnote2,
            color = scheme.onSurfaceVariantSummary,
        )
    }
}

/** 详情页通用区块卡：标题 + 圆角低阴影容器（与前瞻区块一致） */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            title,
            style = MiuixTheme.textStyles.footnote1,
            color = scheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    }
}
