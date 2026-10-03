package com.rzh.valo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 数据表通用视觉（参照喵扑「比赛数据」面板）：
 * [StatsTableCard] 为区块卡（区块标题 + 卡内表头带 + 斑马行），
 * [StatsTableHeaderRow] / [StatsTableCell] / [tableZebraColor] 提供表头带与行内单元格。
 */

/** 数据表区块卡：标题在卡外（右侧可带快捷动作），卡内边距为 0，行内容自行布局（表头带 + 斑马行） */
@Composable
fun StatsTableCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
            if (action != null) {
                Spacer(Modifier.weight(1f))
                action()
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** 表头带行：左侧块标题占剩余宽度，右侧为若干定宽列表头 */
@Composable
fun StatsTableHeaderRow(title: String, columnLabels: List<Pair<String, Dp>>) {
    val scheme = MiuixTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(scheme.onSurface.copy(alpha = 0.035f)),
    ) {
        Text(
            title,
            style = MiuixTheme.textStyles.footnote1,
            color = scheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        columnLabels.forEach { (label, width) -> StatsTableCell(label, width, header = true) }
    }
}

/** 居中定宽单元格：表头/次要为灰色常规，强调为主色加粗，默认前景色 Medium */
@Composable
fun StatsTableCell(
    value: String,
    width: Dp,
    header: Boolean = false,
    secondary: Boolean = false,
    emphasized: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    Box(Modifier.width(width).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text(
            value,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = when {
                header -> FontWeight.Normal
                emphasized -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
            color = when {
                header || secondary -> scheme.onSurfaceVariantSummary
                emphasized -> scheme.primary
                else -> scheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 斑马行底色：奇数行叠加极淡前景色，偶数行透出卡片底色 */
fun tableZebraColor(index: Int, scheme: Colors): Color =
    if (index % 2 == 1) scheme.onSurface.copy(alpha = 0.025f) else Color.Transparent
