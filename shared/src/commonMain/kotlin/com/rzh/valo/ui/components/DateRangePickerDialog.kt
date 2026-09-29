package com.rzh.valo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rzh.valo.data.formatCnDate
import com.rzh.valo.data.plusDays
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日期范围选择（迁移自 Material3 DateRangePicker 的精简实现）：
 * 月历翻页 + 起止两段式选择；只选一天即查单日。周一为每周首日。
 */
@Composable
fun DateRangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    // 月历游标：所选区间的锚点月份（默认当前月）
    var monthAnchor by remember { mutableStateOf((initialStart ?: initialEnd ?: todayOrAnchor()).withDayOfMonth(1)) }

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "选择日期范围",
        summary = run {
            val s = start
            when {
                s == null -> "开始日期 – 结束日期"
                end == null || s == end -> formatCnDate(s)
                else -> "${formatCnDate(s)} – ${formatCnDate(end!!)}"
            }
        },
        onDismissFinished = { },
    ) {
        Column {
            // ---- 月份行 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthAnchor = monthAnchor.minus(1, DateTimeUnit.MONTH) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一月")
                }
                Text(
                    "${monthAnchor.year}年${monthAnchor.monthNumber}月",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { monthAnchor = monthAnchor.plus(1, DateTimeUnit.MONTH) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一月")
                }
            }
            Spacer(Modifier.height(6.dp))
            // ---- 星期表头（周一起始） ----
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // ---- 日历网格 ----
            val firstDay = monthAnchor
            // Java LocalDate 的 dayOfWeek 与 kotlinx 一致（ISO），周一 = 0
            val leadingBlanks = firstDay.dayOfWeek.ordinal
            val daysInMonth = firstDay.monthNumberOfDays()
            val rows = (leadingBlanks + daysInMonth + 6) / 7
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val index = row * 7 + col - leadingBlanks
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (index in 0 until daysInMonth) {
                                val date = firstDay.plusDays(index)
                                DayCell(
                                    date = date,
                                    state = dayState(date, start, end),
                                    onClick = {
                                        when {
                                            start == null || (start != null && end != null) -> {
                                                start = date
                                                end = null
                                            }
                                            date < start!! -> start = date
                                            else -> end = date
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val s = start ?: return@Button
                        onConfirm(s, end ?: s)
                    },
                    enabled = start != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("查询")
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: Int, onClick: () -> Unit) {
    // state：0 普通 · 1 区间起点/终点 · 2 区间中间 · 3 今天（无选中关系时弱提示）
    val scheme = MiuixTheme.colorScheme
    val container = when (state) {
        1 -> scheme.primary
        2 -> scheme.secondaryContainer
        3 -> scheme.surfaceContainerHigh
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val content = when (state) {
        1 -> scheme.onPrimary
        2 -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }
    Box(
        Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (state == 1) FontWeight.Bold else FontWeight.Normal,
            color = content,
        )
    }
}

private fun dayState(date: LocalDate, start: LocalDate?, end: LocalDate?): Int = when {
    date == start || date == end -> 1
    start != null && end != null && date > start && date < end -> 2
    else -> 0
}

private fun todayOrAnchor(): LocalDate = com.rzh.valo.data.todayCn()

/** 该月天数（kotlinx LocalDate 没有 lengthOfMonth，按 1 日 + 1 月 - 1 天推算） */
private fun LocalDate.monthNumberOfDays(): Int {
    val nextMonth = plus(1, DateTimeUnit.MONTH).withDayOfMonth(1)
    return nextMonth.minus(1, DateTimeUnit.DAY).dayOfMonth
}

/** 取当月 1 号 */
private fun LocalDate.withDayOfMonth(day: Int): LocalDate = LocalDate(year, monthNumber, day)
