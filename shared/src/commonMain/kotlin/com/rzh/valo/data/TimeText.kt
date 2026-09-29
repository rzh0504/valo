package com.rzh.valo.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant

/** 当前毫秒时间戳（挂钟时间，供缓存 TTL 与快照时间戳使用） */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** 上海时区的「今天」 */
fun todayCn(): LocalDate = Clock.System.todayIn(CN_ZONE)

/** 加 N 天 */
fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

/** 减 N 个月 */
fun LocalDate.minusMonths(months: Int): LocalDate = minus(months, DateTimeUnit.MONTH)

/** 纪元毫秒 → 上海时区日期 */
fun epochToLocalDate(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(CN_ZONE).date

/** 上海时区日期零点 → 纪元毫秒 */
fun LocalDate.startOfDayMillis(): Long = atStartOfDayIn(CN_ZONE).toEpochMilliseconds()

/** HH:mm（上海时区） */
fun formatTime(epochMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(CN_ZONE).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** M月d日 */
fun formatCnDate(date: LocalDate): String = "${date.monthNumber}月${date.dayOfMonth}日"

/** 星期X */
fun formatCnWeekday(date: LocalDate): String = when (date.dayOfWeek.ordinal) {
    0 -> "星期一"
    1 -> "星期二"
    2 -> "星期三"
    3 -> "星期四"
    4 -> "星期五"
    5 -> "星期六"
    else -> "星期日"
}

/** M月d日 星期X */
fun formatCnDateWithWeekday(date: LocalDate): String = "${formatCnDate(date)} ${formatCnWeekday(date)}"

/** yyyy年M月d日 HH:mm（上海时区） */
fun formatFullTime(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(CN_ZONE)
    return "${dt.year}年${dt.monthNumber}月${dt.dayOfMonth}日 ${formatTime(epochMillis)}"
}

/** 小组件时间行：今天/明天 + HH:mm，更远用 M/d 周X */
fun formatWidgetTime(epochMillis: Long, now: Long): String {
    val date = epochToLocalDate(epochMillis)
    val today = epochToLocalDate(now)
    val days = today.daysUntil(date)
    val prefix = when (days) {
        0 -> "今天"
        1 -> "明天"
        else -> "${date.monthNumber}/${date.dayOfMonth} ${formatCnWeekday(date).removePrefix("星期")}"
    }
    return "$prefix ${formatTime(epochMillis)}"
}

/** 保留 [decimals] 位小数的显示格式（替代 JVM 的 %.0f / %.2f） */
fun Double.formatFixed(decimals: Int): String {
    if (decimals <= 0) return this.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val scaled = (this * factor).let { if (it >= 0) floor(it + 0.5) else ceil(it - 0.5) } / factor
    val text = scaled.toString()
    val dot = text.indexOf('.')
    if (dot < 0) return "$text." + "0".repeat(decimals)
    val missing = decimals - (text.length - dot - 1)
    return if (missing > 0) text + "0".repeat(missing) else text
}

/** 胜率百分比（total 为 0 时显示占位符） */
fun percentText(wins: Int, total: Int): String =
    if (total > 0) "${(wins * 100.0 / total).roundToInt()}%" else "—"
