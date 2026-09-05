package com.rzh.valo.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rzh.valo.MainActivity
import com.rzh.valo.ValoApplication
import com.rzh.valo.data.CN_ZONE
import com.rzh.valo.data.HaojiaoApi
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.filterByLevels
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.theme.valoColorScheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 「近期比赛」桌面小组件：展示进行中/未开赛的比赛，开赛后即使数据源状态
 * 尚未更新也保持展示，直到真正结束才移出；全部结束时回退展示最近完赛的比赛。
 * 每场使用独立信息卡；背景按系统深浅色模式取色，透明度可在应用设置中调节。
 * 数据来自磁盘快照，由 [WidgetRefreshWorker] 每小时刷新一次。
 */
object ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ValoApplication).container
        val data = withContext(Dispatchers.IO) {
            WidgetData(
                items = container.repository.loadSnapshot()?.items.orEmpty(),
                opacity = container.settingsStore.widgetOpacity.first(),
                matchLevels = container.settingsStore.matchLevels.first(),
                themeMode = container.settingsStore.themeMode.first(),
                dynamicColor = container.settingsStore.dynamicColor.first(),
            )
        }
        val darkTheme = when (data.themeMode) {
            ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val palette = WidgetPalette(valoColorScheme(context, darkTheme, data.dynamicColor), data.opacity)
        // 队标并发加载，Coil 磁盘缓存命中时不发网络
        val rows = coroutineScope {
            widgetRows(data.items, data.matchLevels).map { row ->
                async {
                    row.copy(
                        mainLogo = loadLogo(context, row.mainLogoPath),
                        guestLogo = loadLogo(context, row.guestLogoPath),
                    )
                }
            }.awaitAll()
        }
        provideContent {
            Content(
                rows = rows,
                palette = palette,
            )
        }
    }

    private data class WidgetData(
        val items: List<MatchItem>,
        val opacity: Float,
        val matchLevels: Set<String>,
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
    )

    @Composable
    private fun Content(rows: List<RowModel>, palette: WidgetPalette) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.background)
                .padding(10.dp),
        ) {
            if (rows.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无比赛数据，打开应用刷新", style = TextStyle(palette.secondary, fontSize = 12.sp))
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(rows) { row -> WidgetRow(row, palette) }
                }
            }
        }
    }

    @Composable
    private fun WidgetRow(row: RowModel, palette: WidgetPalette) {
        Column(GlanceModifier.fillMaxWidth()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(MATCH_ID_KEY to row.matchId),
                        )
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.timeText,
                        style = TextStyle(palette.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        row.competition,
                        style = TextStyle(palette.secondary, fontSize = 10.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.width(148.dp),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(row.mainTeam, row.mainLogo, palette.secondary)
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            row.mainTeam,
                            style = TextStyle(
                                palette.onCard,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.width(48.dp),
                        )
                        Text(
                            row.score,
                            style = TextStyle(palette.onCard, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            modifier = GlanceModifier.width(54.dp),
                        )
                        Text(
                            row.guestTeam,
                            style = TextStyle(
                                palette.onCard,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.width(48.dp),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        TeamBadge(row.guestTeam, row.guestLogo, palette.secondary)
                    }
                }
            }
            Spacer(GlanceModifier.height(8.dp))
        }
    }

    @Composable
    private fun TeamBadge(name: String, logo: Bitmap?, fallbackColor: ColorProvider) {
        Box(
            modifier = GlanceModifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                Image(
                    provider = ImageProvider(logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.size(22.dp),
                )
            } else {
                Text(
                    name.trim().take(1).uppercase().ifBlank { "?" },
                    style = TextStyle(fallbackColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
        }
    }

    private data class RowModel(
        val matchId: String,
        val timeText: String,
        val competition: String,
        val mainTeam: String,
        val guestTeam: String,
        val score: String,
        val mainLogoPath: String?,
        val guestLogoPath: String?,
        val mainLogo: Bitmap? = null,
        val guestLogo: Bitmap? = null,
    )

    private fun widgetRows(items: List<MatchItem>, matchLevels: Set<String>): List<RowModel> {
        val now = System.currentTimeMillis()
        val levelFiltered = items.filterByLevels(matchLevels)
        // 按状态过滤：进行中/未开赛都保留（开赛时间已过但状态未更新也继续展示），
        // 直到真正结束后才移出；全部结束时回退展示最近的完赛比赛。
        val active = levelFiltered
            .filter { it.status != MatchStatus.FINISHED }
            .sortedBy { it.startTime }
            .take(4)
        val selected = active.ifEmpty {
            levelFiltered.filter { it.status == MatchStatus.FINISHED }.sortedByDescending { it.startTime }.take(4)
        }
        return selected.map { item ->
            val mainParticipant = item.versus?.mainCamp?.firstOrNull()
            val guestParticipant = item.versus?.guestCamp?.firstOrNull()
            val main = mainParticipant?.displayName ?: "待定"
            val guest = guestParticipant?.displayName ?: "待定"
            val competition = item.group?.nameSub ?: item.group?.nameMain ?: item.tournament?.nameMain.orEmpty()
            RowModel(
                matchId = item.id,
                timeText = if (item.status == MatchStatus.LIVE) "进行中" else formatTime(item.startTime, now),
                competition = competition,
                mainTeam = main,
                guestTeam = guest,
                mainLogoPath = mainParticipant?.icon,
                guestLogoPath = guestParticipant?.icon,
                score = if (item.status == MatchStatus.SCHEDULED) {
                    "  VS  "
                } else {
                    "  ${item.versus?.mainScore ?: "0"} : ${item.versus?.guestScore ?: "0"}  "
                },
            )
        }
    }

    private suspend fun loadLogo(context: Context, path: String?): Bitmap? {
        val url = HaojiaoApi.imageUrl(path) ?: return null
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(96)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        return (result as? SuccessResult)?.drawable?.toBitmap()
    }

    private fun formatTime(epochMillis: Long, now: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(CN_ZONE).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(CN_ZONE).toLocalDate()
        val clock = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(CN_ZONE))
        return when {
            date == today -> "今天 $clock"
            date == today.plusDays(1) -> "明天 $clock"
            else -> DateTimeFormatter.ofPattern("M/d E", Locale.CHINA).format(date) + " $clock"
        }
    }

    val MATCH_ID_KEY = ActionParameters.Key<String>(MainActivity.EXTRA_MATCH_ID)
}

/** 小组件与主程序共享同一 Material 3 ColorScheme。 */
private class WidgetPalette(scheme: androidx.compose.material3.ColorScheme, opacity: Float) {
    val background = ColorProvider(scheme.surface.copy(alpha = opacity))
    val card = ColorProvider(scheme.surfaceContainerLow.copy(alpha = opacity))
    val onCard = ColorProvider(scheme.onSurface)
    val secondary = ColorProvider(scheme.onSurfaceVariant)
    val primary = ColorProvider(scheme.primary)
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
