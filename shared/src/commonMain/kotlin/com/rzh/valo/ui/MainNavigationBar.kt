package com.rzh.valo.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
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

/** 悬浮毛玻璃底栏的几何：胶囊高度 + 上下边距（避让手势条，至少 8dp） */
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

    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(50)
    // 页面背景与卡片同色系，胶囊底提亮一档（深色用卡片色、浅色用纯白）再压一层投影，保证边界可辨
    val isDark = scheme.background.luminance() < 0.5f
    val tint = if (isDark) scheme.surfaceContainer.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.75f)
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
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(radius = 12.dp, color = Color.Black, alpha = if (isDark) 0.25f else 0.1f),
                )
                .textureBlur(backdrop = backdrop, shape = shape, blurRadius = 24f)
                .background(tint, shape),
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
                        .background(scheme.primary.copy(alpha = 0.15f), shape),
                )
            }
            // 条目自绘而非 NavigationBarItem：后者内容顶部对齐按整条 64dp 排布，
            // 在胶囊里会偏上；这里居中排布让图标+文字落在选中药丸正中
            Row(Modifier.fillMaxSize()) {
                TABS.forEachIndexed { index, tab ->
                    val selected = currentIndex == index
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(index) },
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = scheme.onSurfaceContainer,
                                modifier = Modifier.size(NavigationBarDefaults.IconSize),
                            )
                            Text(
                                tab.label,
                                color = scheme.onSurfaceContainer,
                                fontSize = NavigationBarDefaults.LabelFontSize,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
