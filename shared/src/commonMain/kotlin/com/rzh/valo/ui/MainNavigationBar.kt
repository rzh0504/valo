package com.rzh.valo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

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

/** 底部页签（今天 / 赛程 / 设置），沿用选中态双图标 */
@Composable
internal fun MainNavigationBar(currentIndex: Int, onSelect: (Int) -> Unit) {
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
}
