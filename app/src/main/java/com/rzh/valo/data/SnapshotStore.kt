package com.rzh.valo.data

import android.content.Context
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
    data class Snapshot(val fetchedAt: Long = 0L, val items: List<MatchItem> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "schedule_snapshot.json")

    @Synchronized
    fun save(items: List<MatchItem>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Snapshot(System.currentTimeMillis(), items)))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    @Synchronized
    fun load(): Snapshot? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString<Snapshot>(file.readText())
    }.getOrNull()
}
