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

/**
 * 深色配色：miuix 默认深色画布为纯黑、卡片与背景同为 #242424，这里改为分层深灰——
 * 画布 #141414，卡片抬升一层，勾选胶囊/骨架块再抬升；蓝色强调色沿用 miuix 默认。
 */
private val DarkColors = darkColorScheme(
    surface = Color(0xFF141414),
    background = Color(0xFF141414),
    surfaceContainer = Color(0xFF1E1E1E),
    // 队标底色与卡片同色（浅色模式下同为白底的行为）
    surfaceVariant = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333),
    dividerLine = Color(0xFF2B2B2B),
    outline = Color(0xFF3C3C3C),
)

/**
 * 浅色配色：miuix 默认卡片为纯白，浅色队标在卡上难以辨认，
 * 卡片与队标底色改为浅灰，画布保持 miuix 默认浅灰白。
 */
private val LightColors = lightColorScheme(
    surfaceContainer = Color(0xFFF2F2F3),
    surfaceVariant = Color(0xFFF2F2F3),
)

/** 应用主题：跟随系统 / 浅色 / 深色 */
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
    val controller = remember(mode) { ThemeController(mode, lightColors = LightColors, darkColors = DarkColors) }
    MiuixTheme(controller = controller, content = content)
}
