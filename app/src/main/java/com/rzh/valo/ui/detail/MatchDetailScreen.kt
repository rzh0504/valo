package com.rzh.valo.ui.detail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.LinkInfo
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.Participant
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
                    CircularProgressIndicator()
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
                else -> state.item?.let { DetailContent(it) }
            }
        }
    }
}

@Composable
private fun DetailContent(item: MatchItem) {
    val versus = item.versus
    val main = versus?.mainCamp?.firstOrNull()
    val guest = versus?.guestCamp?.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TeamBlock(main, winner = item.isFinished && versus?.isMainWin == 1, modifier = Modifier.weight(1f))
            ScoreBlock(item, modifier = Modifier.width(120.dp))
            TeamBlock(guest, winner = item.isFinished && versus?.isMainWin == 2, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
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

        val links = item.links
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            links.distinctBy { it.desc + it.url }.forEach { link ->
                LinkRow(link)
            }
        }
    }
}

@Composable
private fun TeamBlock(participant: Participant?, winner: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        TeamLogo(participant?.icon, size = 56)
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
        trailingContent = { Icon(Icons.Rounded.ExitToApp, contentDescription = "打开") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                }
            },
    )
}
