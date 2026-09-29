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

// 品牌色（VALORANT 红）；动态取色关闭或平台不支持壁纸取色时使用
private val BrandPrimary = Color(0xFFA3382F)
private val BrandOnPrimary = Color(0xFFFFFFFF)
private val BrandPrimaryContainer = Color(0xFFFFDAD5)
private val BrandOnPrimaryContainer = Color(0xFF410002)
private val BrandSecondaryContainer = Color(0xFFE8E0E8)
private val BrandOnSecondaryContainer = Color(0xFF2B2029)

private val BrandPrimaryDark = Color(0xFFFFB4A8)
private val BrandOnPrimaryDark = Color(0xFF650E05)
private val BrandPrimaryContainerDark = Color(0xFF86241C)
private val BrandOnPrimaryContainerDark = Color(0xFFFFDAD5)
private val BrandSecondaryContainerDark = Color(0xFF4A4249)
private val BrandOnSecondaryContainerDark = Color(0xFFE8E0E8)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondaryContainer = BrandSecondaryContainerDark,
    onSecondaryContainer = BrandOnSecondaryContainerDark,
)

/** 应用主题：dynamicColor 走 Monet 动态取色（iOS 从品牌色种子生成，Android 12+ 壁纸取色） */
@Composable
fun ValoTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val mode = when {
        dynamicColor -> ColorSchemeMode.MonetSystem
        themeMode == ThemeMode.LIGHT -> ColorSchemeMode.Light
        themeMode == ThemeMode.DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val controller = rememberBrandController(mode, dynamicColor)
    MiuixTheme(controller = controller, content = content)
}

@Composable
private fun rememberBrandController(mode: ColorSchemeMode, dynamicColor: Boolean): ThemeController =
    remember(mode, dynamicColor) {
        when (mode) {
            ColorSchemeMode.MonetSystem -> ThemeController(mode, keyColor = BrandPrimary)
            ColorSchemeMode.MonetLight -> ThemeController(mode, keyColor = BrandPrimary)
            ColorSchemeMode.MonetDark -> ThemeController(mode, keyColor = BrandPrimary)
            else -> ThemeController(mode, lightColors = LightColors, darkColors = DarkColors)
        }
    }
