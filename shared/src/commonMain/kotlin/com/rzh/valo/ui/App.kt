package com.rzh.valo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.rzh.valo.data.AppContainer
import com.rzh.valo.data.Participant
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.detail.MatchDetailScreen
import com.rzh.valo.ui.home.HomeScreen
import com.rzh.valo.ui.schedule.ScheduleScreen
import com.rzh.valo.ui.settings.AboutScreen
import com.rzh.valo.ui.settings.ScheduleFilterScreen
import com.rzh.valo.ui.settings.SettingsScreen
import com.rzh.valo.ui.team.TeamDetailScreen
import com.rzh.valo.ui.team.TeamScheduleScreen
import com.rzh.valo.ui.theme.ValoTheme
import com.rzh.valo.ui.tournament.TournamentScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/** 小组件点击比赛行携带的深链；由平台入口写入，App 消费后清空 */
class DeepLinkState {
    var pendingMatchId by mutableStateOf<String?>(null)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

val LocalVersionName = staticCompositionLocalOf { "" }

/** 设置变更后刷新桌面小组件的平台回调（Android 实现，其他平台为空） */
val LocalWidgetRefresher = staticCompositionLocalOf<(suspend () -> Unit)?> { null }

/** 兜底 ViewModelStoreOwner：保证 iOS 等无 Activity 宿主的平台 viewModel() 可用 */
private class AppViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * 应用根组件：主题 + miuix-nav 导航。
 * 平台入口（androidApp / iOS）构造 [AppContainer] 后调用。
 */
@Composable
fun App(
    container: AppContainer,
    versionName: String,
    deepLink: DeepLinkState = remember { DeepLinkState() },
    onWidgetDataChanged: (suspend () -> Unit)? = null,
) {
    val themeMode by container.settingsStore.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val fallbackOwner = remember { AppViewModelStoreOwner() }

    ValoTheme(themeMode = themeMode) {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalVersionName provides versionName,
            LocalWidgetRefresher provides onWidgetDataChanged,
            LocalViewModelStoreOwner provides fallbackOwner,
        ) {
            AppRoot(deepLink)
        }
    }
}

@Composable
private fun AppRoot(deepLink: DeepLinkState) {
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember { Navigator(backStack) }
    val openMatch: (String) -> Unit = { navigator.push(Route.Match(it)) }
    val openTournament: (String, String) -> Unit = { id, name -> navigator.push(Route.Tournament(id, name)) }

    // 小组件深链：进入应用后跳转比赛详情
    LaunchedEffect(deepLink.pendingMatchId) {
        deepLink.pendingMatchId?.let {
            navigator.push(Route.Match(it))
            deepLink.pendingMatchId = null
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { navigator.pop() },
    ) {
        entry<Route.Main> {
            MainTabs(
                onOpenMatch = openMatch,
                onOpenTournament = openTournament,
                onOpenScheduleFilter = { navigator.push(Route.ScheduleFilter) },
                onOpenAbout = { navigator.push(Route.About) },
            )
        }
        entry<Route.Match> { route ->
            MatchDetailScreen(
                matchId = route.matchId,
                onBack = { navigator.pop() },
                onOpenTeamInfo = { participant: Participant? ->
                    val id = participant?.id
                    if (!id.isNullOrBlank()) {
                        navigator.push(
                            Route.TeamInfo(
                                teamId = id,
                                name = participant.nameMain.orEmpty().ifBlank { participant.displayName },
                                short = participant.nameShort.orEmpty(),
                                icon = participant.icon.orEmpty(),
                            )
                        )
                    }
                },
                onOpenTournament = openTournament,
            )
        }
        entry<Route.TeamInfo> { route ->
            TeamDetailScreen(
                route = route,
                onBack = { navigator.pop() },
                onOpenSchedule = {
                    navigator.push(
                        Route.TeamSchedule(
                            teamId = route.teamId,
                            name = route.name,
                            short = route.short,
                            icon = route.icon,
                        )
                    )
                },
                onOpenMatch = openMatch,
            )
        }
        entry<Route.TeamSchedule> { route ->
            TeamScheduleScreen(
                route = route,
                onBack = { navigator.pop() },
                onOpenMatch = openMatch,
                onOpenTournament = openTournament,
            )
        }
        entry<Route.Tournament> { route ->
            TournamentScreen(
                tournamentId = route.tournamentId,
                tournamentName = route.name,
                onBack = { navigator.pop() },
                onOpenMatch = openMatch,
            )
        }
        entry<Route.ScheduleFilter> {
            ScheduleFilterScreen(onBack = { navigator.pop() })
        }
        entry<Route.About> {
            AboutScreen(onBack = { navigator.pop() })
        }
    }
}

@Composable
private fun MainTabs(
    onOpenMatch: (String) -> Unit,
    onOpenTournament: (String, String) -> Unit,
    onOpenScheduleFilter: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    // 页签即 pager 页：点底部栏弹簧滑动到目标页（miuix example 惯用形态），
    // 页间也支持横滑；各页 rememberSaveable 状态由 pager 内置的 holder 保管
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            MainNavigationBar(
                // targetPage 在点击动画开始时即指向目标页、手势翻页过半后跟随，高亮始终领先于落位
                currentIndex = pagerState.targetPage,
                onSelect = { index -> scope.launch { pagerState.springAnimateToPage(index) } },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = PagerNavigationSpringSpec,
            ),
            verticalAlignment = Alignment.Top,
        ) { page ->
            when (page) {
                0 -> HomeScreen(onOpenMatch = onOpenMatch)
                1 -> ScheduleScreen(onOpenMatch = onOpenMatch)
                else -> SettingsScreen(
                    onOpenScheduleFilter = onOpenScheduleFilter,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }
}
