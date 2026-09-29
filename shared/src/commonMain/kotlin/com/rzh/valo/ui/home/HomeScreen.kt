package com.rzh.valo.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.formatCnDateWithWeekday
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.OnResumeEffect
import com.rzh.valo.ui.components.SkeletonCards
import com.rzh.valo.ui.components.bouncyPress
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun HomeScreen(onOpenMatch: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(key = "home") {
        HomeViewModel(container.repository, container.settingsStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 回到前台时静默再验证，进程存活的热启动不再滞留旧数据
    OnResumeEffect { viewModel.onResume() }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        // 底部安全区由外层 MainTabs 的 bottomBar 统一处理，内层不再叠加
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "今天",
                largeTitle = formatCnDateWithWeekday(state.date),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> SkeletonCards(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                )
                state.error != null && !state.hasMatches -> HomeError(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                else -> PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    HomeContent(state, viewModel, onOpenMatch)
                }
            }
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, viewModel: HomeViewModel, onOpenMatch: (String) -> Unit) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
    ) {
        item(key = "hero") { HeroHeader(state, viewModel) }

        if (!state.hasMatches) {
            item(key = "empty") { EmptyToday() }
            return@LazyColumn
        }

        val filter = state.filter
        val shownSections = listOf(
            Triple(MatchStatus.LIVE, "进行中", state.live),
            Triple(MatchStatus.SCHEDULED, "未开始", state.scheduled),
            Triple(MatchStatus.FINISHED, "已结束", state.finished),
        ).filter { filter == null || it.first == filter }

        shownSections.forEach { (status, title, matches) ->
            section(
                key = "s$status",
                title = title,
                matches = matches,
                onOpenMatch = onOpenMatch,
                emphasized = status == MatchStatus.LIVE,
            )
        }

        if (shownSections.all { it.third.isEmpty() }) {
            item(key = "filterEmpty") {
                Text(
                    "该状态下今天没有比赛",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: String,
    matches: List<com.rzh.valo.data.MatchItem>,
    onOpenMatch: (String) -> Unit,
    emphasized: Boolean = false,
) {
    if (matches.isEmpty()) return
    item(key = "header_$key") {
        SectionTitle(title, count = matches.size, emphasized = emphasized)
    }
    items(matches, key = { "home_${key}_${it.id}" }) { match ->
        MatchCard(
            item = match,
            onClick = { onOpenMatch(match.id) },
            emphasized = emphasized && match.isLive,
            showCountdown = true,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun HeroHeader(state: HomeUiState, viewModel: HomeViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryChip(
            label = "进行中",
            count = state.live.size,
            selected = state.filter == MatchStatus.LIVE,
            onClick = { viewModel.toggleFilter(MatchStatus.LIVE) },
        )
        SummaryChip(
            label = "未开始",
            count = state.scheduled.size,
            selected = state.filter == MatchStatus.SCHEDULED,
            onClick = { viewModel.toggleFilter(MatchStatus.SCHEDULED) },
        )
        SummaryChip(
            label = "已结束",
            count = state.finished.size,
            selected = state.filter == MatchStatus.FINISHED,
            onClick = { viewModel.toggleFilter(MatchStatus.FINISHED) },
        )
    }
}

/** 可点击的状态筛选胶囊：选中实色，未选中灰色；计数为 0 且未选中时置灰禁点（避免进入空筛选态） */
@Composable
private fun SummaryChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MiuixTheme.colorScheme
    val enabled = count > 0 || selected
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primary else scheme.surfaceContainerHighest,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariantSummary,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .bouncyPress(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Text("$count", style = MiuixTheme.textStyles.footnote1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, emphasized: Boolean) {
    val scheme = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            title,
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) scheme.primary else scheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MiuixTheme.textStyles.footnote1,
            color = scheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun EmptyToday() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
    ) {
        Text(
            "今天没有比赛",
            style = MiuixTheme.textStyles.headline2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "近期比赛会显示在赛程中",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(message, style = MiuixTheme.textStyles.body2)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}
