package com.rzh.valo.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.LinkInfo
import com.rzh.valo.data.MapRoundData
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.data.PlayerMapStats
import com.rzh.valo.data.RoundData
import com.rzh.valo.ui.components.LoadingDots
import com.rzh.valo.ui.components.StatusPill
import com.rzh.valo.ui.components.TeamLogo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(matchId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: MatchDetailViewModel = viewModel { MatchDetailViewModel(app.container.repository, matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("比赛详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingDots()
                }
                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Text(state.error!!, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.retry() }) { Text("重试") }
                }
                else -> state.item?.let { DetailContent(state.item!!, state.round) }
            }
        }
    }
}

@Composable
private fun DetailContent(item: MatchItem, round: RoundData) {
    val versus = item.versus
    val main = versus?.mainCamp?.firstOrNull()
    val guest = versus?.guestCamp?.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- 头部：赛事与对阵 ----
        Text(
            item.group?.nameMain ?: item.tournament?.nameMain.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        val parts = listOfNotNull(item.stage?.name, item.scheduleName?.takeIf { it.isNotBlank() })
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TeamBlock(main, winner = item.isFinished && versus?.isMainWin == 1, modifier = Modifier.weight(1f))
            ScoreBlock(item, modifier = Modifier.width(120.dp))
            TeamBlock(guest, winner = item.isFinished && versus?.isMainWin == 2, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusPill(item.status)
            Spacer(Modifier.width(10.dp))
            Text(
                FULL_TIME_FORMAT.format(Instant.ofEpochMilli(item.startTime)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "BO${item.boNum}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- 地图小局 ----
        if (round.list.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            MapsSection(round)
        } else if (item.status == MatchStatus.SCHEDULED) {
            Spacer(Modifier.height(16.dp))
            Text(
                "比赛开始后可查看地图与选手数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- 相关链接 ----
        val links = item.links
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            links.distinctBy { it.desc + it.url }.forEach { link -> LinkRow(link) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- 地图与小局 ----------

@Composable
private fun MapsSection(round: RoundData) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(round) { selected = 0 }
    val index = selected.coerceIn(0, round.list.size - 1)

    Column {
        Text(
            "地图小局",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(round.list) { mapData ->
                val i = round.list.indexOf(mapData)
                MapPill(
                    label = mapData.map?.displayName ?: "地图 ${i + 1}",
                    score = "${mapData.mainScore?.total ?: 0} : ${mapData.guestScore?.total ?: 0}",
                    selected = i == index,
                    onClick = { selected = i },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(androidx.compose.animation.core.tween(220)) togetherWith fadeOut(androidx.compose.animation.core.tween(160)) },
            label = "mapSwitch",
        ) { i ->
            MapPanel(round.list[i])
        }
    }
}

@Composable
private fun MapPill(label: String, score: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                score,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun MapPanel(map: MapRoundData) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    map.map?.displayName ?: "地图",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${map.mainScore?.total ?: 0} : ${map.guestScore?.total ?: 0}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                map.map?.nameEn.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (map.isOvertime) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "加时赛",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(14.dp))
            HalfScores(map)
            if (map.rounds.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                RoundTimeline(map)
            }
            if (map.players.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                PlayerTable(map)
            }
        }
    }
}

/** 攻防半场比分 */
@Composable
private fun HalfScores(map: MapRoundData) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            HalfCell("进攻", map.mainScore?.atk ?: 0)
            HalfCell("防守", map.mainScore?.def ?: 0)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("半场", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            HalfCell("进攻", map.guestScore?.atk ?: 0)
            HalfCell("防守", map.guestScore?.def ?: 0)
        }
    }
}

@Composable
private fun HalfCell(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** 逐回合胜负时间轴 */
@Composable
private fun RoundTimeline(map: MapRoundData) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall),
        ) {
            map.rounds.forEach { round ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(
                            if (round.winTeam == 1) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            LegendDot(MaterialTheme.colorScheme.primary, "主队回合")
            Spacer(Modifier.width(14.dp))
            LegendDot(MaterialTheme.colorScheme.tertiary, "客队回合")
            Spacer(Modifier.weight(1f))
            Text(
                "共 ${map.rounds.size} 回合",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 本图选手数据：主队/客队两列 */
@Composable
private fun PlayerTable(map: MapRoundData) {
    val mainPlayers = map.players.filter { it.isMain }.sortedWith(compareByDescending<PlayerMapStats> { it.acs ?: 0.0 })
    val guestPlayers = map.players.filter { !it.isMain }.sortedWith(compareByDescending<PlayerMapStats> { it.acs ?: 0.0 })
    Column {
        Row {
            Column(Modifier.weight(1f)) {
                PlayerGroupHeader(map.mainTeam?.short ?: "主队")
                mainPlayers.forEach { PlayerRow(it) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                PlayerGroupHeader(map.guestTeam?.short ?: "客队")
                guestPlayers.forEach { PlayerRow(it) }
            }
        }
    }
}

@Composable
private fun PlayerGroupHeader(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun PlayerRow(player: PlayerMapStats) {
    val kda = "${player.kills}/${player.deaths}/${player.assists}"
    val nick = player.career?.idName?.takeIf { it.isNotBlank() } ?: player.player?.realName.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        TeamLogo(player.hero?.icon, size = 22)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nick,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (player.hero?.nameZh?.isNotBlank() == true) {
                Text(
                    player.hero.nameZh,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            kda,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            player.acs?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------- 通用块 ----------

@Composable
private fun TeamBlock(participant: Participant?, winner: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        TeamLogo(participant?.icon, size = 60)
        Spacer(Modifier.height(8.dp))
        Text(
            participant?.nameMain ?: "待定",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
            color = if (winner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScoreBlock(item: MatchItem, modifier: Modifier = Modifier) {
    val versus = item.versus
    val showScore = (item.isLive || item.isFinished) && versus?.mainCamp?.isNotEmpty() == true
    Box(modifier, contentAlignment = Alignment.Center) {
        if (showScore) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    versus.mainScore ?: "0",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = if (item.isFinished && versus.isMainWin == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.isFinished && versus.isMainWin == 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    " : ",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    versus.guestScore ?: "0",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = if (item.isFinished && versus.isMainWin == 2) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.isFinished && versus.isMainWin == 2) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        } else {
            Text(
                "vs",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(link: LinkInfo) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(link.desc ?: "相关链接", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(link.url.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = "打开") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                }
            },
    )
}
