package com.rzh.valo.ui.detail

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.LinkInfo
import com.rzh.valo.data.MapRoundData
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.MapRecord
import com.rzh.valo.data.Participant
import com.rzh.valo.data.PlayerMapStats
import com.rzh.valo.data.PlayerMatchStats
import com.rzh.valo.data.RoundData
import com.rzh.valo.data.RoundEntry
import com.rzh.valo.data.RoundTeam
import com.rzh.valo.data.formatCnDate
import com.rzh.valo.data.formatFixed
import com.rzh.valo.data.formatFullTime
import com.rzh.valo.data.epochToLocalDate
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.components.StatsTableCard
import com.rzh.valo.ui.components.StatsTableCell
import com.rzh.valo.ui.components.StatsTableHeaderRow
import com.rzh.valo.ui.components.StatusPill
import com.rzh.valo.ui.components.TeamLogo
import com.rzh.valo.ui.components.bouncyPress
import com.rzh.valo.ui.components.tableZebraColor
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 页面内容区的水平内边距 */
private val PAGE_PADDING = 20.dp

/**
 * 让横向列表越出父级 [PAGE_PADDING] 内边距、占满屏宽，
 * 配合等宽 contentPadding 使内容对齐、滚动在屏幕边缘裁切。
 */
private fun Modifier.fullBleed(horizontal: Dp = PAGE_PADDING): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(maxWidth = constraints.maxWidth + (horizontal * 2).roundToPx())
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@Composable
fun MatchDetailScreen(
    matchId: String,
    onBack: () -> Unit,
    onOpenTeamInfo: (Participant?) -> Unit = {},
    onOpenTournament: (String, String) -> Unit = { _, _ -> },
) {
    val container = LocalAppContainer.current
    val viewModel: MatchDetailViewModel = viewModel(key = "match_$matchId") {
        MatchDetailViewModel(container.repository, matchId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "比赛详情",
                scrollBehavior = scrollBehavior,
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
                    InfiniteProgressIndicator()
                }
                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Text(state.error!!, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(text = "重试", onClick = { viewModel.retry() })
                }
                else -> state.item?.let { item ->
                    PullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                        topAppBarScrollBehavior = scrollBehavior,
                    ) {
                        DetailContent(
                            item,
                            state.round,
                            state.foresight,
                            onTeamClick = onOpenTeamInfo,
                            onOpenTournament = onOpenTournament,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    item: MatchItem,
    round: RoundData,
    foresight: ForesightUiState?,
    onTeamClick: (Participant?) -> Unit,
    onOpenTournament: (String, String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val versus = item.versus
    val main = versus?.mainCamp?.firstOrNull()
    val guest = versus?.guestCamp?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(PAGE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- 头部：赛事与对阵（赛事名可点进赛事主页） ----
        val tournamentId = item.tournament?.id
        val tournamentLabel = item.group?.nameMain ?: item.tournament?.nameMain.orEmpty()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (tournamentId != null) {
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenTournament(tournamentId, tournamentLabel) }
            } else {
                Modifier
            },
        ) {
            Text(
                tournamentLabel,
                style = MiuixTheme.textStyles.headline2,
                textAlign = TextAlign.Center,
            )
            if (tournamentId != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "进入赛事主页",
                    tint = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        val parts = listOfNotNull(item.stage?.name, item.scheduleName?.takeIf { it.isNotBlank() })
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                parts.joinToString(" · "),
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TeamBlock(
                main,
                winner = item.isFinished && versus?.isMainWin == 1,
                modifier = Modifier.weight(1f),
                onLogoClick = { onTeamClick(main) },
            )
            ScoreBlock(item, modifier = Modifier.width(120.dp))
            TeamBlock(
                guest,
                winner = item.isFinished && versus?.isMainWin == 2,
                modifier = Modifier.weight(1f),
                onLogoClick = { onTeamClick(guest) },
            )
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
                formatFullTime(item.startTime),
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "BO${item.boNum}",
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
            )
        }

        // ---- 赛前前瞻（未开赛且对阵已定时） ----
        if (item.status == MatchStatus.SCHEDULED && foresight != null && main != null && guest != null) {
            Spacer(Modifier.height(24.dp))
            ForesightSection(foresight, main, guest)
        }

        // ---- 地图小局 ----
        if (round.list.isNotEmpty() || round.all.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            MapsSection(round, versus?.mainScore, versus?.guestScore)
        } else if (item.status == MatchStatus.SCHEDULED) {
            Spacer(Modifier.height(16.dp))
            Text(
                "比赛开始后可查看地图与选手数据",
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
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
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = PAGE_PADDING),
                modifier = Modifier.fullBleed(),
            ) {
                items(links.distinctBy { it.desc + it.url }) { link -> LinkPill(link) }
            }
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

// ---------- 地图与小局 ----------

@Composable
private fun MapsSection(
    round: RoundData,
    mainSeriesScore: String?,
    guestSeriesScore: String?,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(round) { selected = 0 }
    // 全场数据固定为第一个页签，地图页签依次后移
    val mapOffset = if (round.all.isNotEmpty()) 1 else 0
    val tabCount = round.list.size + mapOffset
    val index = selected.coerceIn(0, tabCount - 1)
    val fullMatchIndex = if (round.all.isNotEmpty()) 0 else -1

    Column(Modifier.fillMaxWidth()) {
        Text(
            "比赛数据",
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = PAGE_PADDING),
            modifier = Modifier.align(Alignment.CenterHorizontally).fullBleed(),
        ) {
            if (round.all.isNotEmpty()) {
                item(key = "full") {
                    MapPill(
                        label = "全场数据",
                        selected = index == fullMatchIndex,
                        onClick = { selected = fullMatchIndex },
                    )
                }
            }
            itemsIndexed(round.list) { i, mapData ->
                MapPill(
                    label = mapData.map?.displayName ?: "地图 ${i + 1}",
                    selected = index == i + mapOffset,
                    onClick = { selected = i + mapOffset },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "mapSwitch",
        ) { i ->
            if (i == fullMatchIndex) {
                val first = round.list.firstOrNull()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamStatsCard(
                        team = first?.mainTeam,
                        score = mainSeriesScore,
                        columns = FULL_STAT_COLUMNS,
                        rows = fullTeamRows(round.all, main = true),
                    )
                    TeamStatsCard(
                        team = first?.guestTeam,
                        score = guestSeriesScore,
                        columns = FULL_STAT_COLUMNS,
                        rows = fullTeamRows(round.all, main = false),
                    )
                }
            } else {
                val map = round.list[i - mapOffset]
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MapPanel(map)
                    TeamStatsCard(
                        team = map.mainTeam,
                        score = map.mainScore?.total?.toString(),
                        columns = MAP_STAT_COLUMNS,
                        rows = mapTeamRows(map, main = true),
                    )
                    TeamStatsCard(
                        team = map.guestTeam,
                        score = map.guestScore?.total?.toString(),
                        columns = MAP_STAT_COLUMNS,
                        rows = mapTeamRows(map, main = false),
                    )
                }
            }
        }
    }
}

/** 地图选择药丸（Miaopu 风格）：单行文案，选中淡蓝底蓝字，未选中灰底；弹性按压替代矩形水波纹 */
@Composable
private fun MapPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerHighest,
        contentColor = if (selected) scheme.primary else scheme.onSurface,
        modifier = Modifier
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MapPanel(map: MapRoundData) {
    val scheme = MiuixTheme.colorScheme
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    map.map?.displayName ?: "地图",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${map.mainScore?.total ?: 0} : ${map.guestScore?.total ?: 0}",
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                map.map?.nameEn.orEmpty(),
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceVariantSummary,
            )
            if (map.isOvertime) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "加时赛",
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.primary,
                )
            }

            Spacer(Modifier.height(14.dp))
            if (map.rounds.isNotEmpty()) {
                RoundGrid(map)
                Spacer(Modifier.height(10.dp))
                RoundGridLegend(showUnplayed = !map.isEnd)
            }
        }
    }
}

/** 逐回合网格图例 */
@Composable
private fun RoundGridLegend(showUnplayed: Boolean) {
    val scheme = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendCell(DEFENSE_WIN_COLOR, "防守胜")
            LegendCell(ATTACK_WIN_COLOR, "进攻胜")
            LegendCell(scheme.surfaceContainerHighest, "落败")
            if (showUnplayed) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, scheme.outline, RoundedCornerShape(3.dp)),
                )
                Text("未赛", style = MiuixTheme.textStyles.footnote2, color = scheme.onSurfaceVariantSummary)
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
    val scheme = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MiuixTheme.textStyles.footnote2, color = scheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun LegendIcon(mode: Int, label: String) {
    val scheme = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        WinIcon(mode, size = 11, tint = scheme.onSurfaceVariantSummary)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MiuixTheme.textStyles.footnote2, color = scheme.onSurfaceVariantSummary)
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
internal fun gridCellCount(map: MapRoundData): Int {
    val size = map.rounds.size
    if (map.isEnd || size == 0) return size
    return ((size + ROUND_GROUP_SIZE - 1) / ROUND_GROUP_SIZE) * ROUND_GROUP_SIZE
}

@Composable
private fun RoundGrid(map: MapRoundData) {
    val scheme = MiuixTheme.colorScheme
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
                        color = scheme.onSurfaceVariantSummary,
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
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun RoundCellBox(entry: RoundEntry?, teamId: Int) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    when {
        entry == null || entry.winTeam == 0 -> Box(
            Modifier
                .size(CELL_SIZE)
                .clip(shape)
                .border(1.dp, scheme.outline.copy(alpha = 0.6f), shape),
        )
        entry.winTeam != teamId -> Box(
            Modifier
                .size(CELL_SIZE)
                .clip(shape)
                .background(scheme.surfaceContainerHighest),
        )
        else -> {
            val bg = when (entry.winCamp) {
                1 -> DEFENSE_WIN_COLOR
                2 -> ATTACK_WIN_COLOR
                else -> scheme.primary
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
                    size = Size(w * 0.52f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.06f),
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

// ---------- 数据面板：分队卡片 + 固定选手列 + 可横向滚动的数据列 ----------

/** 数据列定义：表头文案 + 固定列宽 */
private data class StatColumn(val label: String, val width: Dp = 68.dp)

/** 表格一行的视图数据：左侧英雄区 + 姓名 + 各数据列取值 */
private data class StatsRow(val heroIcons: List<String?>, val name: String, val cells: List<String>)

/** 单图数据列 */
private val MAP_STAT_COLUMNS = listOf(
    StatColumn("K/D/A"),
    StatColumn("首杀/首死"),
    StatColumn("ACS"),
    StatColumn("ADR"),
    StatColumn("KAST"),
    StatColumn("HS"),
)

/** 全场数据列：末尾多一个回合数 */
private val FULL_STAT_COLUMNS = MAP_STAT_COLUMNS + StatColumn("回合")

/** 接口对未统计的数值/百分比字段回 0，统一显示为 — */
private fun Double?.statText(percent: Boolean = false): String =
    if (this == null || this <= 0.0) "—" else formatFixed(0) + if (percent) "%" else ""

private fun PlayerMapStats.toStatsRow(): StatsRow = StatsRow(
    heroIcons = listOf(hero?.icon),
    name = career?.idName?.takeIf { it.isNotBlank() } ?: player?.realName.orEmpty(),
    cells = listOf(
        "$kills/$deaths/$assists",
        "$fk/$fd",
        acs.statText(),
        adr.statText(),
        kast.statText(percent = true),
        hs.statText(percent = true),
    ),
)

private fun PlayerMatchStats.toStatsRow(): StatsRow = StatsRow(
    heroIcons = hero.map { it.icon },
    name = career?.idName?.takeIf { it.isNotBlank() } ?: player?.realName.orEmpty(),
    cells = listOf(
        "$kills/$deaths/$assists",
        "$fk/$fd",
        acs.statText(),
        adr.statText(),
        kast.statText(percent = true),
        hs.statText(percent = true),
        rounds.toString(),
    ),
)

/** 单队选手行（按 ACS 降序） */
private fun mapTeamRows(map: MapRoundData, main: Boolean): List<StatsRow> =
    map.players.filter { it.isMain == main }.sortedByDescending { it.acs ?: 0.0 }.map { it.toStatsRow() }

private fun fullTeamRows(all: List<PlayerMatchStats>, main: Boolean): List<StatsRow> =
    all.filter { it.isMain == main }.sortedByDescending { it.acs ?: 0.0 }.map { it.toStatsRow() }

/**
 * 单队数据卡（参照喵扑比赛数据面板）：队头为队标 + 队名 + 比分；
 * 「选手」列固定不滚动，仅数据列可横向滚动，姓名始终可见；斑马行分隔。
 */
@Composable
private fun TeamStatsCard(
    team: RoundTeam?,
    score: String?,
    columns: List<StatColumn>,
    rows: List<StatsRow>,
) {
    val scheme = MiuixTheme.colorScheme
    val statsScroll = rememberScrollState()
    val headerBand = scheme.onSurface.copy(alpha = 0.035f)
    val oddRow = scheme.onSurface.copy(alpha = 0.025f)
    Card(
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                TeamLogo(team?.icon, size = 22)
                Spacer(Modifier.width(8.dp))
                Text(
                    team?.short ?: team?.name ?: "队伍",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (score != null) {
                    Text(score, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(132.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(32.dp).background(headerBand),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            "选手",
                            style = MiuixTheme.textStyles.footnote1,
                            color = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    rows.forEachIndexed { index, row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(if (index % 2 == 1) oddRow else Color.Transparent)
                                .padding(start = 12.dp),
                        ) {
                            Box(Modifier.width(40.dp)) {
                                row.heroIcons.take(3).forEachIndexed { j, icon ->
                                    TeamLogo(
                                        icon,
                                        size = 18,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.offset(x = (j * 12).dp).zIndex(-j.toFloat()),
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                row.name,
                                style = MiuixTheme.textStyles.footnote1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).horizontalScroll(statsScroll)) {
                    Row(Modifier.height(32.dp)) {
                        columns.forEach { column ->
                            StatCell(column.label, header = true, width = column.width)
                        }
                    }
                    rows.forEachIndexed { index, row ->
                        Row(
                            Modifier
                                .height(36.dp)
                                .background(if (index % 2 == 1) oddRow else Color.Transparent),
                        ) {
                            row.cells.forEach { StatCell(it, header = false, width = 68.dp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StatCell(value: String, header: Boolean, width: Dp) {
    val scheme = MiuixTheme.colorScheme
    Box(Modifier.width(width).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text(
            value,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (header) FontWeight.Normal else FontWeight.Medium,
            color = if (header) scheme.onSurfaceVariantSummary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- 通用块 ----------

@Composable
private fun TeamBlock(
    participant: Participant?,
    winner: Boolean,
    modifier: Modifier = Modifier,
    onLogoClick: (() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (participant != null && onLogoClick != null) {
                        Modifier.clickable(onClick = onLogoClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            TeamLogo(participant?.icon, size = 60, shape = CircleShape)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            participant?.nameMain ?: "待定",
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
            color = if (winner) scheme.primary else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScoreBlock(item: MatchItem, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    val versus = item.versus
    val showScore = (item.isLive || item.isFinished) && versus?.mainCamp?.isNotEmpty() == true
    Box(modifier, contentAlignment = Alignment.Center) {
        if (showScore) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    versus.mainScore ?: "0",
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = if (item.isFinished && versus.isMainWin == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.isFinished && versus.isMainWin == 1) {
                        scheme.primary
                    } else {
                        scheme.onSurface
                    },
                )
                Text(
                    " : ",
                    style = MiuixTheme.textStyles.title3,
                    color = scheme.onSurfaceVariantSummary,
                )
                Text(
                    versus.guestScore ?: "0",
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = if (item.isFinished && versus.isMainWin == 2) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.isFinished && versus.isMainWin == 2) {
                        scheme.primary
                    } else {
                        scheme.onSurface
                    },
                )
            }
        } else {
            Text(
                "vs",
                style = MiuixTheme.textStyles.title2,
                color = scheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 低调的外部链接胶囊（打开系统浏览器）；弹性按压替代矩形水波纹 */
@Composable
private fun LinkPill(link: LinkInfo) {
    val scheme = MiuixTheme.colorScheme
    val urlOpener = rememberLocalUrlOpener()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null) { urlOpener(link.url) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                link.desc ?: "链接",
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ExitToApp,
                contentDescription = "打开",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun rememberLocalUrlOpener(): (String?) -> Unit {
    val handler = androidx.compose.ui.platform.LocalUriHandler.current
    return { url -> url?.let { runCatching { handler.openUri(it) } } }
}

// ---------- 赛前前瞻 ----------

@Composable
private fun ForesightSection(foresight: ForesightUiState, main: Participant, guest: Participant) {
    val scheme = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            "赛前前瞻",
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        when {
            foresight.loading -> Card(
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) { InfiniteProgressIndicator() }
            }

            foresight.failed -> Card(
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
            ) {
                Text(
                    "前瞻数据加载失败",
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                foresight.fight?.let { fight ->
                    StatsTableCard(title = "历史交手") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    main.displayName,
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                )
                                Spacer(Modifier.width(6.dp))
                                TeamLogo(main.icon, size = 22)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${fight.mainScoreCount?.win ?: 0} : ${fight.guestScoreCount?.win ?: 0}",
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                TeamLogo(guest.icon, size = 22)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    guest.displayName,
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        StatsTableHeaderRow(
                            "对手",
                            listOf("日期" to 56.dp, "比分" to 64.dp, "结果" to 52.dp),
                        )
                        fight.matchList.forEachIndexed { index, match ->
                            TeamRecentRow(index, main, match)
                        }
                    }
                }

                val maps = foresight.maps
                if (maps.isNotEmpty()) {
                    StatsTableCard(title = "地图胜率") {
                        StatsTableHeaderRow(
                            "地图",
                            listOf(main.displayName to 72.dp, guest.displayName to 72.dp),
                        )
                        maps.forEachIndexed { index, map ->
                            MapRecordRow(index, map)
                        }
                    }
                }
            }
        }
    }
}

/** 单张地图的双方历史胜率行（斑马行），占优一侧高亮 */
@Composable
private fun MapRecordRow(index: Int, map: MapRecord) {
    val scheme = MiuixTheme.colorScheme
    val mainBetter = map.mainWins * map.guestMatches > map.guestWins * map.mainMatches
    val guestBetter = map.guestWins * map.mainMatches > map.mainWins * map.guestMatches
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(tableZebraColor(index, scheme))
            .padding(start = 12.dp),
    ) {
        TeamLogo(map.icon, size = 22)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                map.displayName,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!map.nameEn.isNullOrBlank() && map.nameEn != map.displayName) {
                Text(
                    map.nameEn,
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MapRateCell(map.mainWins, map.mainMatches, mainBetter)
        MapRateCell(map.guestWins, map.guestMatches, guestBetter)
    }
}

/** 单侧胜率单元格：有场次显示百分比（占优高亮），无数据显示占位符 */
@Composable
private fun MapRateCell(wins: Int, matches: Int, highlight: Boolean) {
    val scheme = MiuixTheme.colorScheme
    Box(Modifier.width(72.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text(
            if (matches > 0) "${(wins * 100.0 / matches).roundToInt()}%" else "—",
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                highlight -> scheme.primary
                matches == 0 -> scheme.onSurfaceVariantSummary
                else -> scheme.onSurface
            },
            maxLines = 1,
        )
    }
}

// ---------- H2H 交手列表行 ----------

/** 历史交手表行（以 [team] 视角，斑马行）：对手 | 日期 | 比分 | 结果 */
@Composable
private fun TeamRecentRow(index: Int, team: Participant, match: MatchItem) {
    val scheme = MiuixTheme.colorScheme
    val versus = match.versus ?: return
    val side = teamSide(versus, team)
    if (side == 0) return
    val teamScore = if (side == 1) versus.mainScore else versus.guestScore
    val oppScore = if (side == 1) versus.guestScore else versus.mainScore
    val won = versus.isMainWin == side
    val opponent = (if (side == 1) versus.guestCamp else versus.mainCamp)?.firstOrNull()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(tableZebraColor(index, scheme))
            .padding(start = 12.dp),
    ) {
        TeamLogo(opponent?.icon, size = 22)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                opponent?.displayName ?: "待定",
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                match.group?.nameMain ?: match.tournament?.nameMain.orEmpty(),
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatsTableCell(formatCnDate(epochToLocalDate(match.startTime)), 56.dp, secondary = true)
        StatsTableCell("${teamScore ?: "0"} : ${oppScore ?: "0"}", 64.dp, emphasized = won, secondary = !won)
        Box(Modifier.width(52.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (match.isFinished) ResultPill(won) else StatusPill(match.status)
        }
    }
}

/** 胜负结果胶囊：胜用主色容器，负用中性灰 */
@Composable
private fun ResultPill(won: Boolean) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (won) scheme.primaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (won) scheme.onPrimaryContainer else scheme.onSurfaceVariantSummary,
    ) {
        Text(
            if (won) "胜" else "负",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
