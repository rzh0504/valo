package com.rzh.valo.widget

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.rzh.valo.ValoApplication
import java.util.concurrent.TimeUnit

/**
 * 小组件数据刷新任务：每小时在联网时拉取一次近期赛程写入快照并更新小组件。
 * 这是小组件唯一的网络请求来源，保证请求频率恒定在每小时一次。
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as ValoApplication).container.repository
        return try {
            val (start, end) = repository.widgetWindow()
            val items = repository.schedule(start, end, force = true)
            repository.saveSnapshot(items, start, end)
            updateAllScheduleWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.w("valo", "小组件刷新失败（第 ${runAttemptCount + 1} 次）", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "valo_widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
