package com.rzh.valo.ui.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.ui.components.LoadingDots
import com.rzh.valo.ui.components.MatchCard
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
            else -> HomeContent(state, onOpenMatch)
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, onOpenMatch: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") { HeroHeader(state) }

        if (!state.hasMatches) {
            item(key = "empty") { EmptyToday() }
            return@LazyColumn
        }

        section("live", "进行中", state.live, onOpenMatch, emphasized = true)
        section("scheduled", "未开始", state.scheduled, onOpenMatch)
        section("finished", "已结束", state.finished, onOpenMatch)
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
private fun HeroHeader(state: HomeUiState) {
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
            SummaryChip("进行中", state.live.size, container = MaterialTheme.colorScheme.primaryContainer)
            SummaryChip("未开始", state.scheduled.size, container = MaterialTheme.colorScheme.secondaryContainer)
            SummaryChip("已结束", state.finished.size, container = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, container: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            "$label $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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
