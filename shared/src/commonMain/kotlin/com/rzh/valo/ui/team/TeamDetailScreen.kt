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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.PlayerStatsRow
import com.rzh.valo.data.RosterMember
import com.rzh.valo.data.TeamBase
import com.rzh.valo.data.TeamMapStats
import com.rzh.valo.data.TeamRecord
import com.rzh.valo.data.TeamReward
import com.rzh.valo.data.TeamStats
import com.rzh.valo.data.formatFixed
import com.rzh.valo.data.percentText
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.Route
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.TeamLogo
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 战队详情：基础信息、战队数据、地图胜率、赛事名次、选手名单与选手数据，赛程跳完整赛程页 */
@Composable
fun TeamDetailScreen(
    route: Route.TeamInfo,
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: TeamDetailViewModel = viewModel(key = "teaminfo_${route.teamId}") {
        TeamDetailViewModel(container.repository, route.teamId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
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
                        if (roster.active.isNotEmpty() || roster.organization.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            TeamRosterCard(roster.active, roster.organization)
                        }
                    }
                    val activePlayers = state.playerStats
                        .filter { it.isActive && it.rounds > 0 }
                        .sortedByDescending { it.acs }
                    if (activePlayers.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PlayerStatsCard(activePlayers)
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "查看完整赛程",
                            onClick = onOpenSchedule,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TeamHeaderCard(base: TeamBase?, route: Route.TeamInfo) {
    val scheme = MiuixTheme.colorScheme
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(base?.icon ?: route.icon.takeIf { it.isNotBlank() }, size = 64, shape = CircleShape)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        base?.displayName?.ifBlank { route.name } ?: route.name,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                    )
                    val meta = listOfNotNull(
                        base?.nameShort?.takeIf { it.isNotBlank() } ?: route.short.takeIf { it.isNotBlank() },
                        base?.zoneName,
                    )
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            meta.joinToString(" · "),
                            style = MiuixTheme.textStyles.footnote1,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                    listOfNotNull(
                        base?.city?.takeIf { it.isNotBlank() },
                        base?.establishDate?.takeIf { it.isNotBlank() },
                    ).takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it.joinToString(" · "),
                            style = MiuixTheme.textStyles.footnote2,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            val describe = base?.describe?.takeIf { it.isNotBlank() } ?: return@Column
            Spacer(Modifier.height(12.dp))
            var expanded by remember { mutableStateOf(false) }
            Text(
                describe,
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
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
    SectionCard(title = "地图胜率") {
        maps.forEachIndexed { index, map ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(map.icon, size = 22)
                Spacer(Modifier.width(8.dp))
                Text(
                    map.displayName,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${map.games}局",
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(12.dp))
                val rate = if (map.games > 0) map.wins * 100.0 / map.games else 0.0
                Text(
                    "${rate.roundToInt()}%",
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rate >= 50) scheme.primary else scheme.onSurface,
                )
            }
        }
    }
}

/** 赛事名次与奖金（最近 5 条） */
@Composable
private fun TeamRewardCard(rewards: List<TeamReward>) {
    val scheme = MiuixTheme.colorScheme
    SectionCard(title = "赛事名次") {
        rewards.take(5).forEachIndexed { index, reward ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reward.rank.orEmpty(),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        reward.group?.nameMain.orEmpty(),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${reward.matchWinCount}胜${reward.matchCount - reward.matchWinCount}负 · ${reward.date.orEmpty()}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    reward.bonus.orEmpty(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 选手名单：现役在前（替补/不活跃带标注），教练组以小节附后 */
@Composable
private fun TeamRosterCard(active: List<RosterMember>, organization: List<RosterMember>) {
    val scheme = MiuixTheme.colorScheme
    SectionCard(title = "选手名单") {
        active.forEachIndexed { index, member ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            RosterRow(member)
        }
        if (organization.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "教练组",
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))
            organization.forEachIndexed { index, member ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                RosterRow(member)
            }
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

/** 选手数据（现役，按 ACS 降序）；列与详情页选手对位表同款宽度节奏 */
@Composable
private fun PlayerStatsCard(players: List<PlayerStatsRow>) {
    val scheme = MiuixTheme.colorScheme
    SectionCard(title = "选手数据") {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "选手",
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            StatsHeaderCell("ACS")
            StatsHeaderCell("KD")
            StatsHeaderCell("ADR")
            StatsHeaderCell("KAST")
        }
        Spacer(Modifier.height(8.dp))
        players.forEachIndexed { index, player ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            player.career?.idName.orEmpty(),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        player.heroes.take(3).forEach { hero ->
                            TeamLogo(hero.icon, size = 16)
                            Spacer(Modifier.width(2.dp))
                        }
                    }
                }
                StatsValueCell(player.acs.formatFixed(0), emphasized = index == 0)
                StatsValueCell(player.kd.formatFixed(2))
                StatsValueCell(player.adr.formatFixed(0))
                StatsValueCell("${player.kast.roundToInt()}%")
            }
        }
    }
}

@Composable
private fun StatsHeaderCell(label: String) {
    Text(
        label,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.End,
        modifier = Modifier.width(46.dp),
    )
}

@Composable
private fun StatsValueCell(value: String, emphasized: Boolean = false) {
    Text(
        value,
        style = MiuixTheme.textStyles.body2,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End,
        modifier = Modifier.width(46.dp),
    )
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
