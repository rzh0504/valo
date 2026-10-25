package com.rzh.valo.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.TeamReward
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.Route
import com.rzh.valo.ui.components.LoadingPane
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

/** 战队全部赛事名次：与详情页共用行组件与仓库缓存 */
@Composable
fun TeamRewardsScreen(
    route: Route.TeamRewards,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: TeamRewardsViewModel = viewModel(key = "teamrewards_${route.teamId}") {
        TeamRewardsViewModel(container.repository, route.teamId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "赛事名次",
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
                    label = "正在获取赛事名次",
                )
                state.error != null && state.rewards.isEmpty() -> ErrorPane(
                    message = state.error!!,
                    onRetry = viewModel::retry,
                )
                else -> PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    RewardsList(state.rewards)
                }
            }
        }
    }
}

@Composable
private fun RewardsList(rewards: List<TeamReward>) {
    val scheme = MiuixTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (rewards.isEmpty()) {
            item(key = "empty") {
                Text(
                    "暂无赛事名次",
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                )
            }
        } else {
            item(key = "count") {
                Text(
                    "共 ${rewards.size} 项赛事",
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
            item(key = "table") {
                Card(
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        RewardTableHeader()
                        rewards.forEachIndexed { index, reward -> RewardRow(reward, index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(message, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}
