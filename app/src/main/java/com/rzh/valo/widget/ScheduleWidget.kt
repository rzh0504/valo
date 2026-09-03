package com.rzh.valo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rzh.valo.MainActivity
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「近期比赛」桌面小组件：展示未来几场未开赛/进行中的比赛，
 * 无未来赛程时回退展示最近已结束的比赛。数据来自磁盘快照，
 * 由 [WidgetRefreshWorker] 每小时刷新一次，添加小组件时自动启动。
 */
object ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as ValoApplication).container.repository
        val snapshot = withContext(Dispatchers.IO) { repository.loadSnapshot() }
        provideContent { Content(snapshot?.items.orEmpty()) }
    }

    @Composable
    private fun Content(items: List<MatchItem>) {
        Box(
            modifier = GlanceModifier.fillMaxSize().background(WidgetColors.Background).padding(12.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    "无畏契约赛程",
                    style = TextStyle(WidgetColors.OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(8.dp))
                val rows = widgetRows(items)
                if (rows.isEmpty()) {
                    Text("暂无比赛数据，打开应用刷新", style = TextStyle(WidgetColors.Secondary, fontSize = 12.sp))
                } else {
                    rows.forEach { row -> WidgetRow(row) }
                }
            }
        }
    }

    @Composable
    private fun WidgetRow(row: RowModel) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(MATCH_ID_KEY to row.matchId),
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.timeText, style = TextStyle(WidgetColors.Secondary, fontSize = 10.sp))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                row.lineText,
                style = TextStyle(WidgetColors.OnBackground, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }

    private data class RowModel(
        val matchId: String,
        val timeText: String,
        val lineText: String,
    )

    private fun widgetRows(items: List<MatchItem>): List<RowModel> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val upcoming = items
            .filter { it.status == MatchStatus.LIVE || (it.status == MatchStatus.SCHEDULED && it.startTime >= now) }
            .sortedBy { it.startTime }
            .take(5)
        val selected = upcoming.ifEmpty {
            items.filter { it.status == MatchStatus.FINISHED }.sortedByDescending { it.startTime }.take(4)
        }
        return selected.map { item ->
            val main = item.versus?.mainCamp?.firstOrNull()?.displayName ?: "待定"
            val guest = item.versus?.guestCamp?.firstOrNull()?.displayName ?: "待定"
            val teams = if (item.status == MatchStatus.SCHEDULED) {
                "$main vs $guest"
            } else {
                "$main ${item.versus?.mainScore ?: 0} : ${item.versus?.guestScore ?: 0} $guest"
            }
            val sub = item.group?.nameSub ?: item.group?.nameMain ?: item.tournament?.nameMain.orEmpty()
            RowModel(
                matchId = item.id,
                timeText = formatTime(item.startTime, zone, now),
                lineText = if (sub.isBlank()) teams else "$teams · $sub",
            )
        }
    }

    private fun formatTime(epochMillis: Long, zone: ZoneId, now: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val clock = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(zone))
        return when {
            date == today -> "今天 $clock"
            date == today.plusDays(1) -> "明天 $clock"
            else -> DateTimeFormatter.ofPattern("M/d E", Locale.CHINA).format(date) + " $clock"
        }
    }

    private fun formatClock(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

    val MATCH_ID_KEY = ActionParameters.Key<String>(MainActivity.EXTRA_MATCH_ID)}

private object WidgetColors {
    val Background = ColorProvider(Color(0xFF151519))
    val OnBackground = ColorProvider(Color(0xFFF2F2F5))
    val Secondary = ColorProvider(Color(0xFF9A9AA3))
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}
