package com.rzh.valo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rzh.valo.data.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 应用主题：跟随系统 / 浅色 / 深色，直接使用 miuix 默认配色（蓝色强调色） */
@Composable
fun ValoTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val mode = when (themeMode) {
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.DARK -> ColorSchemeMode.Dark
        ThemeMode.SYSTEM -> ColorSchemeMode.System
    }
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller, content = content)
}
