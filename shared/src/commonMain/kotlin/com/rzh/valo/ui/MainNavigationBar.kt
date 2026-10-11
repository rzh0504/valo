package com.rzh.valo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.graphics.Brush
import com.rzh.valo.data.NavBarStyle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private data class TabSpec(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TABS = listOf(
    TabSpec("今天", Icons.Outlined.Today, Icons.Rounded.Today),
    TabSpec("赛程", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    TabSpec("赛事", Icons.Outlined.EmojiEvents, Icons.Rounded.EmojiEvents),
    TabSpec("设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/** 底部页签（今天 / 赛程 / 赛事 / 设置） */
@Composable
internal fun MainNavigationBar(
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    style: NavBarStyle = NavBarStyle.DEFAULT,
    // 悬浮样式需要 pagerState 让指示条随滑动连续移动；默认样式忽略
    pagerState: PagerState? = null,
) {
    when (style) {
        NavBarStyle.DEFAULT -> NavigationBar(showDivider = false) {
            TABS.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = currentIndex == index,
                    onClick = { onSelect(index) },
                    icon = if (currentIndex == index) tab.selectedIcon else tab.icon,
                    label = tab.label,
                )
            }
        }

        NavBarStyle.FLOATING -> pagerState?.let { FloatingNavPill(it, onSelect) }
    }
}

// 悬浮栏几何与状态透明度数值，移植自 YumeBox FloatingBottomBarContent
private val FLOATING_BAR_HEIGHT = 56.dp
private val FLOATING_INDICATOR_HEIGHT = 48.dp
private val FLOATING_BAR_HORIZONTAL_PADDING = 36.dp
private val FLOATING_BAR_TOP_PADDING = 6.dp
private val FLOATING_BAR_BOTTOM_GAP = 12.dp
private val FLOATING_CONTENT_INSET = 4.dp
private val FLOATING_ICON_BOX_SIZE = 20.dp

/**
 * 悬浮胶囊导航条（结构移植自 YumeBox FloatingBottomBarContent）：
 * 胶囊容器 + 双层发丝描边 + 随 pager 连续滑动的主题蓝指示胶囊 + 底部渐变纱罩。
 * 颜色适配 valo：容器与卡片同为 surfaceContainer，未选中用 onSurface 60%。
 * 滑动过程中指示条位移只发生在布局阶段（offset 内读 pager 状态），重组仅在翻页过半换色时发生。
 */
@Composable
private fun FloatingNavPill(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val tabsCount = TABS.size
    val page = pagerState.currentPage
    val containerColor = MiuixTheme.colorScheme.surfaceContainer
    val selectedColor = MiuixTheme.colorScheme.primary
    val unselectedColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
    val indicatorColor = selectedColor.copy(alpha = 0.12f)
    // 亮色下容器与画布同为浅灰系，轮廓靠阴影与深色发丝撑起；暗色下改用白描边
    val isLightTheme = containerColor.luminance() > 0.5f
    val shadowColor = if (isLightTheme) Color.Black.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.24f)
    val outerBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.20f)
    val innerBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.08f)

    val density = LocalDensity.current
    val bottomSafeInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val surfaceWidthPx = remember { mutableIntStateOf(0) }
    val contentInsetPx = remember(density) { with(density) { (FLOATING_CONTENT_INSET * 2).toPx() } }
    val tabWidthPx by remember(tabsCount) {
        derivedStateOf {
            (surfaceWidthPx.intValue - contentInsetPx).coerceAtLeast(0f) / tabsCount.toFloat()
        }
    }

    // 底部渐变纱罩（参考 YumeBox BottomEdgeScrim）：穿入栏区的内容逐渐隐入底色，
    // 亮色为白、暗色为 surface；比栏区向上多画 28dp（越出布局边界），不改变 bottomBar 的测量高度
    val scrimColor = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        MiuixTheme.colorScheme.surface
    } else {
        MiuixTheme.colorScheme.background
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val scrimTopPx = -28.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to scrimColor.copy(alpha = 0f),
                        0.25f to scrimColor.copy(alpha = 0.55f),
                        0.55f to scrimColor.copy(alpha = 0.88f),
                        1f to scrimColor,
                        startY = scrimTopPx,
                        endY = size.height,
                    ),
                    topLeft = Offset(x = 0f, y = scrimTopPx),
                    size = Size(width = size.width, height = size.height - scrimTopPx),
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = FLOATING_BAR_HORIZONTAL_PADDING,
                    end = FLOATING_BAR_HORIZONTAL_PADDING,
                    top = FLOATING_BAR_TOP_PADDING,
                    bottom = bottomSafeInset + FLOATING_BAR_BOTTOM_GAP,
                )
                .onSizeChanged { surfaceWidthPx.intValue = it.width }
                .graphicsLayer {
                    shape = CircleShape
                    clip = false
                    shadowElevation = 7.dp.toPx()
                    ambientShadowColor = shadowColor
                    spotShadowColor = shadowColor
                }
                .height(FLOATING_BAR_HEIGHT)
                .clip(CircleShape)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (tabWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .padding(FLOATING_CONTENT_INSET)
                        .align(Alignment.CenterStart)
                        .offset {
                            val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, (tabsCount - 1).toFloat())
                            IntOffset((progress * tabWidthPx).roundToInt(), 0)
                        }
                        .width(with(density) { tabWidthPx.toDp() })
                        .height(FLOATING_INDICATOR_HEIGHT)
                        .background(indicatorColor, CircleShape),
                )
            }

            Row(
                modifier = Modifier
                    .padding(FLOATING_CONTENT_INSET)
                    .height(FLOATING_INDICATOR_HEIGHT)
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEachIndexed { index, tab ->
                    FloatingNavItem(
                        selected = index == page,
                        icon = if (index == page) tab.selectedIcon else tab.icon,
                        label = tab.label,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        onClick = { if (index != page) onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Box(Modifier.matchParentSize().border(0.3.dp, outerBorderColor, CircleShape))
            Box(Modifier.matchParentSize().padding(1.dp).border(0.2.dp, innerBorderColor, CircleShape))
        }
    }
}

/** 悬浮条条目：weight 均分整条，胶囊裁剪 + 无涟漪点击 */
@Composable
private fun FloatingNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemColor = if (selected) selectedColor else unselectedColor
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(FLOATING_ICON_BOX_SIZE), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = itemColor,
            )
        }
        Text(
            text = label,
            color = itemColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
