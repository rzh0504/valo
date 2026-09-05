package com.rzh.valo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.detail.MatchDetailScreen
import com.rzh.valo.ui.home.HomeScreen
import com.rzh.valo.ui.schedule.ScheduleScreen
import com.rzh.valo.ui.settings.SettingsScreen
import com.rzh.valo.ui.theme.ValoTheme

class MainActivity : ComponentActivity() {

    /** 小组件点击比赛行时携带的 match_id，导航到详情后清空 */
    private val pendingMatchId: MutableState<String?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 仅首次创建时消费深链 extra：旋转等重建时 intent 仍带着同一 extra，
        // 重复读取会把用户再次导航到详情页
        if (savedInstanceState == null) {
            pendingMatchId.value = intent?.getStringExtra(EXTRA_MATCH_ID)
        }
        val app = application as ValoApplication
        setContent {
            val settings = app.container.settingsStore
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(true)
            ValoTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = dynamicColor,
            ) {
                AppRoot(pendingMatchId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_MATCH_ID)?.let { pendingMatchId.value = it }
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
    }
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TOP_LEVEL = listOf(
    TopLevelDestination("home", "今天", Icons.Outlined.Today, Icons.Rounded.Today),
    TopLevelDestination("schedule", "赛程", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    TopLevelDestination("settings", "设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
private fun AppRoot(pendingMatchId: MutableState<String?>) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showBottomBar) {
                ValoNavigationBar(currentRoute) { route -> navController.goTab(route) }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            // 页签间切换用淡入淡出，避免硬切
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
            modifier = Modifier
                .fillMaxSize()
                // 只吃底部导航高度：Scaffold 默认会把状态栏 inset 塞进 top padding，
                // 与各页面自己的 statusBarsPadding 叠加出双倍空白，故顶部由页面自理
                .then(if (showBottomBar) Modifier.padding(bottom = padding.calculateBottomPadding()) else Modifier),
        ) {
            composable("home") { HomeScreen(onOpenMatch = { navController.navigate("match/$it") }) }
            composable("schedule") { ScheduleScreen(onOpenMatch = { navController.navigate("match/$it") }) }
            composable("settings") { SettingsScreen() }
            composable(
                "match/{matchId}",
                // 详情页从右侧滑入，返回时滑出
                enterTransition = {
                    slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220))
                },
            ) { entry ->
                val matchId = entry.arguments?.getString("matchId").orEmpty()
                MatchDetailScreen(matchId = matchId, onBack = { navController.popBackStack() })
            }
        }
    }

    LaunchedEffect(pendingMatchId.value) {
        pendingMatchId.value?.let {
            navController.navigate("match/$it") { launchSingleTop = true }
            pendingMatchId.value = null
        }
    }
}

@Composable
private fun ValoNavigationBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        TOP_LEVEL.forEach { destination ->
            val selected = currentRoute == destination.route
            // Expressive：选中指示块带弹性动画
            val indicatorColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "navIndicator",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.12f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "navIconScale",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination.route) },
                icon = {
                    Icon(
                        if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = indicatorColor),
            )
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
