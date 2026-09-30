package com.rzh.valo.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class TabSpec(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TABS = listOf(
    TabSpec("今天", Icons.Outlined.Today, Icons.Rounded.Today),
    TabSpec("赛程", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    TabSpec("设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/** 悬浮毛玻璃底栏的几何：上边距 + 胶囊高度 + 下边距（避让手势条，至少 8dp） */
private const val PILL_HEIGHT_DP = 64
private const val PILL_MARGIN_DP = 8

/** 列表滚动末端需要让出的底部空间：毛玻璃开启时为悬浮底栏总占位，关闭时为 0 */
@Composable
internal fun bottomBarInset(blurActive: Boolean): Dp {
    if (!blurActive) return 0.dp
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return (PILL_HEIGHT_DP + PILL_MARGIN_DP).dp + maxOf(PILL_MARGIN_DP.dp, navInset)
}

/** 底部页签（今天 / 赛程 / 设置），沿用选中态双图标；开启毛玻璃后改为悬浮胶囊并实时模糊身后内容 */
@Composable
internal fun MainNavigationBar(
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop? = null,
    blurActive: Boolean = false,
) {
    if (!blurActive || backdrop == null) {
        NavigationBar(showDivider = false) {
            TABS.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = currentIndex == index,
                    onClick = { onSelect(index) },
                    icon = if (currentIndex == index) tab.selectedIcon else tab.icon,
                    label = tab.label,
                )
            }
        }
        return
    }

    val shape = RoundedCornerShape(50)
    val tint = MiuixTheme.colorScheme.surfaceContainer
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = PILL_MARGIN_DP.dp)
            .padding(bottom = maxOf(PILL_MARGIN_DP.dp, navInset)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PILL_HEIGHT_DP.dp)
                .textureBlur(backdrop = backdrop, shape = shape, blurRadius = 24f)
                .background(tint.copy(alpha = 0.55f), shape),
        ) {
            // 选中药丸：仅悬浮底栏有，随选中页签弹簧滑动到目标格
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellWidth = maxWidth / TABS.size
                val indicatorWidth = cellWidth - 16.dp
                val indicatorX by animateDpAsState(
                    targetValue = cellWidth * currentIndex + (cellWidth - indicatorWidth) / 2,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
                    label = "navPillX",
                )
                Box(
                    Modifier
                        .offset(x = indicatorX, y = 8.dp)
                        .size(width = indicatorWidth, height = PILL_HEIGHT_DP.dp - 16.dp)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f), shape),
                )
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentIndex == index,
                        onClick = { onSelect(index) },
                        icon = if (currentIndex == index) tab.selectedIcon else tab.icon,
                        label = tab.label,
                    )
                }
            }
        }
    }
}
