package com.rzh.valo.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.data.LinkInfo
import com.rzh.valo.data.MapRoundData
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.data.PlayerMapStats
import com.rzh.valo.data.PlayerMatchStats
import com.rzh.valo.data.RoundData
import com.rzh.valo.data.RoundEntry
import com.rzh.valo.data.RoundTeam
import com.rzh.valo.ui.components.LoadingDots
import com.rzh.valo.ui.components.StatusPill
import com.rzh.valo.ui.components.TeamLogo
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA).withZone(CN_ZONE)

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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
        if (round.list.isNotEmpty() || round.all.isNotEmpty()) {
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
            Spacer(Modifier.height(8.dp))
            Text(
                "相关链接",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(links.distinctBy { it.desc + it.url }) { link -> LinkPill(link) }
            }
        }
    }
}

// ---------- 地图与小局 ----------

@Composable
private fun MapsSection(round: RoundData) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(round) { selected = 0 }
    val tabCount = round.list.size + if (round.all.isNotEmpty()) 1 else 0
    val index = selected.coerceIn(0, tabCount - 1)
    val fullMatchIndex = round.list.size

    Column {
        Text(
            "比赛数据",
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
            if (round.all.isNotEmpty()) {
                item {
                    MapPill(
                        label = "全场数据",
                        score = "${round.all.size} 人",
                        selected = index == fullMatchIndex,
                        onClick = { selected = fullMatchIndex },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(androidx.compose.animation.core.tween(220)) togetherWith fadeOut(androidx.compose.animation.core.tween(160)) },
            label = "mapSwitch",
        ) { i ->
            if (i == fullMatchIndex) {
                FullMatchPanel(
                    all = round.all,
                    mainTeam = round.list.firstOrNull()?.mainTeam,
                    guestTeam = round.list.firstOrNull()?.guestTeam,
                )
            } else {
                MapPanel(round.list[i])
            }
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
            if (map.rounds.isNotEmpty()) {
                RoundGrid(map)
                Spacer(Modifier.height(10.dp))
                RoundGridLegend(showUnplayed = !map.isEnd)
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

/** 逐回合网格图例 */
@Composable
private fun RoundGridLegend(showUnplayed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendCell(DEFENSE_WIN_COLOR, "防守胜")
            LegendCell(ATTACK_WIN_COLOR, "进攻胜")
            LegendCell(MaterialTheme.colorScheme.surfaceContainerHighest, "落败")
            if (showUnplayed) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
                )
                Text("未赛", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendIcon(MODE_ELIMINATE, "歼灭")
            LegendIcon(MODE_DETONATE, "引爆")
            LegendIcon(MODE_DEFUSE, "拆除")
            LegendIcon(MODE_TIME, "时间耗尽")
        }
    }
}

@Composable
private fun LegendCell(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendIcon(mode: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WinIcon(mode, size = 11, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- 逐回合网格（进攻红 / 防守绿 / 落败灰） ----------

private val ATTACK_WIN_COLOR = Color(0xFFE5484D)
private val DEFENSE_WIN_COLOR = Color(0xFF2FA356)

/** mode 实测语义：1 时间耗尽 · 2 引爆 · 3 歼灭 · 4 拆除 */
private const val MODE_TIME = 1
private const val MODE_DETONATE = 2
private const val MODE_ELIMINATE = 3
private const val MODE_DEFUSE = 4

private const val ROUND_GROUP_SIZE = 12

/** 网格总格数：完赛按实际回合数，未完赛补空位到整组，方便看出剩余回合 */
private fun gridCellCount(map: MapRoundData): Int {
    val size = map.rounds.size
    if (map.isEnd || size == 0) return size
    return ((size + ROUND_GROUP_SIZE - 1) / ROUND_GROUP_SIZE) * ROUND_GROUP_SIZE
}

@Composable
private fun RoundGrid(map: MapRoundData) {
    val total = gridCellCount(map)
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        // 回合号
        Row {
            Spacer(Modifier.width(64.dp))
            repeat(total) { i ->
                if (i > 0 && i % ROUND_GROUP_SIZE == 0) Spacer(Modifier.width(8.dp))
                Box(Modifier.width(CELL_SIZE), contentAlignment = Alignment.Center) {
                    Text(
                        "${i + 1}",
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (i < total - 1) Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        RoundTeamRow(map.mainTeam, map, total, teamId = 1)
        Spacer(Modifier.height(4.dp))
        RoundTeamRow(map.guestTeam, map, total, teamId = 2)
    }
}

@Composable
private fun RoundTeamRow(team: RoundTeam?, map: MapRoundData, total: Int, teamId: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamSlot(team)
        repeat(total) { i ->
            if (i > 0 && i % ROUND_GROUP_SIZE == 0) Spacer(Modifier.width(8.dp))
            RoundCellBox(entry = map.rounds.getOrNull(i), teamId = teamId)
            if (i < total - 1) Spacer(Modifier.width(2.dp))
        }
    }
}

private val CELL_SIZE = 20.dp

/** 行首队伍槽：logo 20 + 间距 6 + 队名 38，固定 64dp 供回合号行对齐 */
@Composable
private fun TeamSlot(team: RoundTeam?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(64.dp),
    ) {
        TeamLogo(team?.icon, size = 20)
        Spacer(Modifier.width(6.dp))
        Text(
            team?.short ?: team?.name.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun RoundCellBox(entry: RoundEntry?, teamId: Int) {
    val shape = MaterialTheme.shapes.extraSmall
    when {
        entry == null || entry.winTeam == 0 -> Box(
            Modifier
                .size(CELL_SIZE)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
        )
        entry.winTeam != teamId -> Box(
            Modifier
                .size(CELL_SIZE)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        else -> {
            val bg = when (entry.winCamp) {
                1 -> DEFENSE_WIN_COLOR
                2 -> ATTACK_WIN_COLOR
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                Modifier
                    .size(CELL_SIZE)
                    .clip(shape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                WinIcon(entry.mode, size = 12)
            }
        }
    }
}

/** 获胜方式小图标（白色绘于色块上，图例中可换色） */
@Composable
private fun WinIcon(mode: Int, size: Int, tint: Color = Color.White) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        when (mode) {
            MODE_DETONATE -> {
                // 爆能器：束身瓶体 + 宽底座
                drawPath(
                    Path().apply {
                        moveTo(w * 0.38f, h * 0.82f)
                        lineTo(w * 0.38f, h * 0.4f)
                        quadraticTo(w * 0.5f, h * 0.12f, w * 0.62f, h * 0.4f)
                        lineTo(w * 0.62f, h * 0.82f)
                        close()
                    },
                    tint,
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.24f, h * 0.82f),
                    size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
                )
            }
            MODE_DEFUSE -> {
                // 剪线钳：交叉刀刃 + 双圆环手柄
                drawLine(tint, Offset(w * 0.14f, h * 0.08f), Offset(w * 0.5f, h * 0.56f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.86f, h * 0.08f), Offset(w * 0.5f, h * 0.56f), stroke.width, StrokeCap.Round)
                drawCircle(tint, radius = w * 0.13f, center = Offset(w * 0.3f, h * 0.82f), style = stroke)
                drawCircle(tint, radius = w * 0.13f, center = Offset(w * 0.7f, h * 0.82f), style = stroke)
            }
            MODE_TIME -> {
                // 沙漏
                drawPath(
                    Path().apply {
                        moveTo(w * 0.2f, h * 0.1f)
                        lineTo(w * 0.8f, h * 0.1f)
                        lineTo(w * 0.5f, h * 0.5f)
                        close()
                    },
                    tint,
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.2f, h * 0.9f)
                        lineTo(w * 0.8f, h * 0.9f)
                        lineTo(w * 0.5f, h * 0.5f)
                        close()
                    },
                    tint,
                )
            }
            else -> {
                // 歼灭：交叉剑
                drawLine(tint, Offset(w * 0.16f, h * 0.12f), Offset(w * 0.84f, h * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.84f, h * 0.12f), Offset(w * 0.16f, h * 0.88f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

/** 本图选手数据：按队伍分组的单列表，ID 不截断，顶部带 KDA/ACS 列标签 */
@Composable
private fun PlayerTable(map: MapRoundData) {
    val mainPlayers = map.players.filter { it.isMain }.sortedByDescending { it.acs ?: 0.0 }
    val guestPlayers = map.players.filter { !it.isMain }.sortedByDescending { it.acs ?: 0.0 }
    Column {
        PlayerGroupHeader(map.mainTeam, listOf("KDA" to 72.dp, "ACS" to 38.dp))
        mainPlayers.forEach { PlayerRow(it) }
        Spacer(Modifier.height(10.dp))
        PlayerGroupHeader(map.guestTeam, listOf("KDA" to 72.dp, "ACS" to 38.dp))
        guestPlayers.forEach { PlayerRow(it) }
    }
}

@Composable
private fun PlayerGroupHeader(team: RoundTeam?, columns: List<Pair<String, Dp>>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        TeamLogo(team?.icon, size = 18)
        Spacer(Modifier.width(6.dp))
        Text(
            team?.short ?: team?.name ?: "队伍",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        columns.forEachIndexed { index, (label, width) ->
            if (index == 0) Spacer(Modifier.width(10.dp)) else Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(width),
            )
        }
    }
}

@Composable
private fun PlayerRow(player: PlayerMapStats) {
    val nick = player.career?.idName?.takeIf { it.isNotBlank() } ?: player.player?.realName.orEmpty()
    val kda = "${player.kills}/${player.deaths}/${player.assists}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        TeamLogo(player.hero?.icon, size = 24, shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            nick,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            kda,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            player.acs?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

/** 全场比赛汇总，与地图小局复用同一数据容器。 */
@Composable
private fun FullMatchPanel(all: List<PlayerMatchStats>, mainTeam: RoundTeam?, guestTeam: RoundTeam?) {
    val mainPlayers = all.filter { it.isMain }.sortedByDescending { it.acs ?: 0.0 }
    val guestPlayers = all.filter { !it.isMain }.sortedByDescending { it.acs ?: 0.0 }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            PlayerGroupHeader(mainTeam, listOf("KDA" to 72.dp, "ADR" to 42.dp, "ACS" to 42.dp))
            mainPlayers.forEach { PlayerMatchRow(it) }
            Spacer(Modifier.height(10.dp))
            PlayerGroupHeader(guestTeam, listOf("KDA" to 72.dp, "ADR" to 42.dp, "ACS" to 42.dp))
            guestPlayers.forEach { PlayerMatchRow(it) }
        }
    }
}

@Composable
private fun PlayerMatchRow(player: PlayerMatchStats) {
    val nick = player.career?.idName?.takeIf { it.isNotBlank() } ?: player.player?.realName.orEmpty()
    val kda = "${player.kills}/${player.deaths}/${player.assists}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Box(Modifier.width(44.dp)) {
            player.hero.take(3).forEachIndexed { i, hero ->
                TeamLogo(
                    hero.icon,
                    size = 18,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.offset(x = (i * 13).dp).zIndex(-i.toFloat()),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nick,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${player.rounds} 回合",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            kda,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            player.adr?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            player.acs?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
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

/** 低调的外部链接胶囊 */
@Composable
private fun LinkPill(link: LinkInfo) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.clickable {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                link.desc ?: "链接",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ExitToApp,
                contentDescription = "打开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
