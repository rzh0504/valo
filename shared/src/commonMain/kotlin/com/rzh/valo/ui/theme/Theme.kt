package com.rzh.valo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.rzh.valo.data.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

// 品牌色（VALORANT 红），仅覆盖强调色；中性色沿用 miuix 默认（正常深浅配色）
private val BrandPrimary = Color(0xFFA3382F)
private val BrandOnPrimary = Color(0xFFFFFFFF)
private val BrandPrimaryContainer = Color(0xFFFFDAD5)
private val BrandOnPrimaryContainer = Color(0xFF410002)

private val BrandPrimaryDark = Color(0xFFFFB4A8)
private val BrandOnPrimaryDark = Color(0xFF650E05)
private val BrandPrimaryContainerDark = Color(0xFF86241C)
private val BrandOnPrimaryContainerDark = Color(0xFFFFDAD5)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
)

/** 应用主题：跟随系统 / 浅色 / 深色，使用 miuix 默认中性配色 + 品牌强调色 */
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
    val controller = remember(mode) {
        ThemeController(mode, lightColors = LightColors, darkColors = DarkColors)
    }
    MiuixTheme(controller = controller, content = content)
}
