package com.rzh.valo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rzh.valo.data.MATCH_LEVELS
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.LocalAppContainer
import com.rzh.valo.ui.LocalVersionName
import com.rzh.valo.ui.LocalWidgetRefresher
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val versionName = LocalVersionName.current
    val widgetRefresher = LocalWidgetRefresher.current
    val viewModel: SettingsViewModel = viewModel(key = "settings") {
        SettingsViewModel(container, widgetRefresher)
    }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val widgetOpacity by viewModel.widgetOpacity.collectAsStateWithLifecycle()
    val matchLevels by viewModel.matchLevels.collectAsStateWithLifecycle()
    var opacityDraft by remember(widgetOpacity) { mutableFloatStateOf(widgetOpacity) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState()),
    ) {
        SmallTitle(text = "外观")
        SettingsCard {
            Text("主题模式", style = MiuixTheme.textStyles.body1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            FilterSegmentsTheme(
                selected = themeMode.id,
                onSelect = { viewModel.setThemeMode(ThemeMode.from(it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            SwitchPreference(
                title = "动态取色",
                summary = "使用系统壁纸配色（不支持时回退主题色）",
                checked = dynamicColor,
                onCheckedChange = { viewModel.setDynamicColor(it) },
            )
        }

        SmallTitle(text = "赛程过滤")
        SettingsCard {
            Text(
                "赛事级别（可多选，未标注级别的比赛始终显示）",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                MATCH_LEVELS.forEach { level ->
                    LevelChip(
                        level = level,
                        selected = level in matchLevels,
                        onClick = { viewModel.setMatchLevel(level, level !in matchLevels) },
                    )
                }
            }
        }

        SmallTitle(text = "小组件")
        SettingsCard {
            SliderPreference(
                title = "背景不透明度",
                summary = "${(opacityDraft * 100).toInt()}% · 拖动后松手即应用",
                value = opacityDraft,
                onValueChange = { opacityDraft = it },
                onValueChangeFinished = { viewModel.setWidgetOpacity(opacityDraft) },
                valueRange = 0.2f..1f,
            )
        }

        SmallTitle(text = "关于")
        SettingsCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "版本 $versionName",
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "数据来自 haojiao.cc，仅用于学习目的。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 主题模式三段选择（跟随系统 / 浅色 / 深色） */
@Composable
private fun FilterSegmentsTheme(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    top.yukonga.miuix.kmp.basic.TabRow(
        tabs = ThemeMode.entries.map { it.label },
        selectedTabIndex = ThemeMode.entries.indexOfFirst { it.id == selected }.coerceAtLeast(0),
        onTabSelected = { index -> onSelect(ThemeMode.entries[index].id) },
        modifier = modifier.fillMaxWidth(),
    )
}

/** 赛事级别多选胶囊 */
@Composable
private fun LevelChip(level: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(if (selected || true) 1f else 0.5f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            level,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        )
    }
}

/** miuix 卡片分组容器 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        content = { content() },
    )
}
