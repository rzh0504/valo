package com.rzh.valo.ui.player

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.PlayerBase
import com.rzh.valo.data.PlayerCareer
import com.rzh.valo.data.PlayerCareerStats
import com.rzh.valo.data.PlayerHeroStat
import com.rzh.valo.data.TeamRef
import com.rzh.valo.data.formatFixed
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.components.LoadingPane
import com.rzh.valo.ui.components.SectionCard
import com.rzh.valo.ui.components.StatsTableCard
import com.rzh.valo.ui.components.StatsTableCell
import com.rzh.valo.ui.components.StatsTableHeaderRow
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.components.tableZebraColor
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

/** 选手详情：基础资料、所属战队、简介、职业生涯与赛事成绩 */
@Composable
fun PlayerDetailScreen(
    playerId: String,
    fallbackName: String,
    fallbackIcon: String,
    onBack: () -> Unit,
    onOpenTeam: (String, String, String, String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: PlayerDetailViewModel = viewModel(key = "player_$playerId") {
        PlayerDetailViewModel(container.repository, playerId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = state.base?.displayName?.takeIf { it.isNotBlank() }
        ?: fallbackName.takeIf { it.isNotBlank() }
        ?: "选手详情"

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingPane(
                    modifier = Modifier.fillMaxSize(),
                    label = "正在获取选手资料",
                )
                state.failed -> ErrorPane(onRetry = viewModel::retry)
                else -> PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    PlayerContent(
                        state = state,
                        fallbackName = fallbackName,
                        fallbackIcon = fallbackIcon,
                        onOpenTeam = onOpenTeam,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerContent(
    state: PlayerDetailUiState,
    fallbackName: String,
    fallbackIcon: String,
    onOpenTeam: (String, String, String, String) -> Unit,
) {
    val base = state.base
    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        PlayerHeaderCard(base, fallbackName, fallbackIcon)

        base?.team?.takeIf { it.id.isNotBlank() }?.let { team ->
            Spacer(Modifier.height(12.dp))
            SectionCard("所属战队") { TeamRow(team, onOpenTeam) }
        }

        state.careerStats?.let { stats ->
            Spacer(Modifier.height(12.dp))
            CareerStatsCard(stats)
        }

        state.heroData?.heroes?.takeIf { it.isNotEmpty() }?.let { heroes ->
            Spacer(Modifier.height(12.dp))
            HeroStatsCard(heroes)
        }

        if (state.careers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard("职业生涯") {
                Column {
                    state.careers.forEachIndexed { index, career ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        CareerRow(career, onOpenTeam)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlayerHeaderCard(base: PlayerBase?, fallbackName: String, fallbackIcon: String) {
    val scheme = MiuixTheme.colorScheme
    val icon = base?.icon?.takeIf { it.isNotBlank() } ?: fallbackIcon.takeIf { it.isNotBlank() }
    val name = base?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "选手" }
    val realName = base?.realName?.takeIf { it.isNotBlank() }
        ?: base?.realNameEn?.takeIf { it.isNotBlank() }
    val meta = listOfNotNull(
        base?.nationality?.takeIf { it.isNotBlank() },
        base?.birth?.takeIf { it.isNotBlank() }?.let { "生日 $it" },
        base?.position?.takeIf { it.isNotBlank() },
        base?.role?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            TeamLogo(icon, size = 72, shape = CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (realName != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        realName,
                        style = MiuixTheme.textStyles.footnote1,
                        color = scheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        base?.nationalityIcon?.takeIf { it.isNotBlank() }?.let {
                            TeamLogo(it, size = 14)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            meta,
                            style = MiuixTheme.textStyles.footnote2,
                            color = scheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamRef, onOpenTeam: (String, String, String, String) -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onOpenTeam(team.id, team.name.orEmpty(), team.short.orEmpty(), team.icon.orEmpty())
            },
    ) {
        TeamLogo(team.icon, size = 32)
        Spacer(Modifier.width(10.dp))
        Text(
            team.name.orEmpty(),
            style = MiuixTheme.textStyles.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "进入战队主页",
            tint = scheme.onSurfaceVariantSummary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CareerRow(career: PlayerCareer, onOpenTeam: (String, String, String, String) -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val team = career.team
    val clickable = team?.id?.isNotBlank() == true
    val range = listOfNotNull(
        career.startDate?.takeIf { it.isNotBlank() },
        career.endDate?.takeIf { it.isNotBlank() },
    ).joinToString(" – ")
    val meta = listOfNotNull(
        career.roleLabel.takeIf { it.isNotBlank() },
        range.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.clickable {
                        onOpenTeam(
                            team!!.id,
                            team.name.orEmpty(),
                            team.short.orEmpty(),
                            team.icon.orEmpty(),
                        )
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        TeamLogo(team?.icon, size = 30)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                team?.name.orEmpty().ifBlank { "—" },
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (clickable) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "进入战队主页",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 生涯数据（S 级赛事）：总战绩一行，个人指标三行 */
@Composable
private fun CareerStatsCard(stats: PlayerCareerStats) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "生涯数据",
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "仅统计 S 级赛事",
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Card(
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        "总场次",
                        stats.matchCount.toString(),
                        "${stats.matchWinCount}胜 ${stats.matchCount - stats.matchWinCount}负",
                        Modifier.weight(1f),
                    )
                    StatTile("总回合", stats.roundCount.toString(), modifier = Modifier.weight(1f))
                    StatTile("回合胜率", stats.roundWinRateText(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("击杀", stats.kills.toString(), modifier = Modifier.weight(1f))
                    StatTile("助攻", stats.assists.toString(), modifier = Modifier.weight(1f))
                    StatTile("阵亡", stats.deaths.toString(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("KMAX", stats.maxKills.toString(), modifier = Modifier.weight(1f))
                    StatTile("ACS", stats.acs.statText(1), modifier = Modifier.weight(1f))
                    StatTile("ADR", stats.adr.statText(1), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("KD", stats.kd.statText(2), modifier = Modifier.weight(1f))
                    StatTile("KDA", stats.kda.statText(2), modifier = Modifier.weight(1f))
                    StatTile("KAST", stats.kast.statText(1, percent = true), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String = "", modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .background(scheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.footnote2,
            color = scheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            sub,
            style = MiuixTheme.textStyles.footnote2,
            color = scheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

private fun PlayerCareerStats.roundWinRateText(): String =
    if (roundCount <= 0) "—" else (roundWinCount * 100.0 / roundCount).formatFixed(1) + "%"

private fun Double?.statText(decimals: Int, percent: Boolean = false): String =
    if (this == null || this <= 0.0) "—" else formatFixed(decimals) + if (percent) "%" else ""

/** 特工数据：近三个月各特工的使用回合与核心指标 */
@Composable
private fun HeroStatsCard(heroes: List<PlayerHeroStat>) {
    val scheme = MiuixTheme.colorScheme
    StatsTableCard(
        title = "特工数据",
        action = {
            Text(
                "近三个月",
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
            )
        },
    ) {
        StatsTableHeaderRow(
            "特工",
            listOf("回合" to 48.dp, "ACS" to 48.dp, "K/D/A" to 76.dp, "ADR" to 48.dp),
        )
        heroes.forEachIndexed { index, hero ->
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
                    TeamLogo(hero.hero?.icon, size = 20)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        hero.hero?.nameZh?.takeIf { it.isNotBlank() } ?: hero.hero?.nameEn.orEmpty(),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatsTableCell(hero.rounds.toString(), 48.dp, secondary = true)
                StatsTableCell(hero.acs.statText(1), 48.dp, secondary = true)
                StatsTableCell("${hero.kills}/${hero.deaths}/${hero.assists}", 76.dp, secondary = true)
                StatsTableCell(hero.adr.statText(1), 48.dp, secondary = true)
            }
        }
    }
}

@Composable
private fun ErrorPane(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text("选手资料加载失败", style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}
