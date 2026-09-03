package com.rzh.valo.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

/** 重新渲染所有已添加的小组件（仅读快照，不发网络请求） */
suspend fun updateAllScheduleWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(ScheduleWidget::class.java).forEach { glanceId ->
        ScheduleWidget.update(context, glanceId)
    }
}
