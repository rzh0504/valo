package com.rzh.valo.data

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 磁盘快照：最近一次赛程数据落盘，供桌面小组件离线渲染、应用冷启动兜底。
 */
class SnapshotStore(context: Context) {

    @Serializable
    data class Snapshot(
        val fetchedAt: Long = 0L,
        val items: List<MatchItem> = emptyList(),
        val homeFetchedAt: Long = 0L,
        val homeItems: List<MatchItem> = emptyList(),
        /**
         * [items] 实际拉取时使用的时间窗 [coveredStart, coveredEnd)。
         * 赛程页与小组件任务的窗口不同，读取方须确认请求窗口被覆盖才能命中；
         * 旧快照缺省为 0，视作不覆盖任何窗口。
         */
        val coveredStart: Long = 0L,
        val coveredEnd: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "schedule_snapshot.json")

    @Synchronized
    fun save(items: List<MatchItem>, coveredStart: Long, coveredEnd: Long) {
        val previous = load()
        write(
            Snapshot(
                fetchedAt = System.currentTimeMillis(),
                items = items,
                homeFetchedAt = previous?.homeFetchedAt ?: 0L,
                homeItems = previous?.homeItems.orEmpty(),
                coveredStart = coveredStart,
                coveredEnd = coveredEnd,
            )
        )
    }

    @Synchronized
    fun saveHome(items: List<MatchItem>) {
        val previous = load()
        write(
            Snapshot(
                fetchedAt = previous?.fetchedAt ?: 0L,
                items = previous?.items.orEmpty(),
                homeFetchedAt = System.currentTimeMillis(),
                homeItems = items,
                coveredStart = previous?.coveredStart ?: 0L,
                coveredEnd = previous?.coveredEnd ?: 0L,
            )
        )
    }

    private fun write(snapshot: Snapshot) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { Log.w("valo", "快照写入失败", it) }
    }

    @Synchronized
    fun load(): Snapshot? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString<Snapshot>(file.readText())
    }.onFailure { Log.w("valo", "快照读取失败", it) }.getOrNull()
}
