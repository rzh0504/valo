package com.rzh.valo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.ui.components.LoadingDots
import com.rzh.valo.ui.components.MatchCard
import com.rzh.valo.ui.components.bouncyPress
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenMatch: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ValoApplication
    val viewModel: HomeViewModel = viewModel { HomeViewModel(app.container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingDots()
            }
            state.error != null && !state.hasMatches -> HomeError(state.error!!, onRetry = { viewModel.refresh() })
            else -> HomeContent(state, viewModel, onOpenMatch)
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, viewModel: HomeViewModel, onOpenMatch: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        MatchCard(item = match, onClick = { onOpenMatch(match.id) }, emphasized = emphasized && match.isLive)
    }
}

@Composable
private fun HeroHeader(state: HomeUiState, viewModel: HomeViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            "今天",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        val dateText = state.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
        Text(
            dateText,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
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
}

/** 可点击的状态筛选胶囊：选中实色，未选中灰色 */
@Composable
private fun SummaryChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, emphasized: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyToday() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp),
    ) {
        Text(
            "今天没有比赛",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "去「赛程」看看近期其他比赛吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
