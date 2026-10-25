package com.rzh.valo.data

import com.rzh.valo.util.logWarn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

private const val MAX_API_CACHE_ENTRIES = 300

@Serializable
private data class ApiCacheEntry(val json: String = "", val savedAt: Long = 0L)

@Serializable
private data class ApiCacheFile(val entries: Map<String, ApiCacheEntry> = emptyMap())

/** 磁盘长缓存条目：内容 + 写入时间，供内存缓存恢复剩余有效期 */
class CachedJson(val json: String, val savedAt: Long)

/**
 * 磁盘快照：最近一次赛程数据落盘，供桌面小组件离线渲染、应用冷启动兜底。
 */
class SnapshotStore(baseDir: String) {

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
    private val fs = FileSystem.SYSTEM
    private val file = "$baseDir/schedule_snapshot.json".toPath()
    private val seriesFile = "$baseDir/series_index.json".toPath()
    private val apiCacheFile = "$baseDir/api_cache.json".toPath()
    private val mutex = Mutex()
    private var apiCache: ApiCacheFile? = null

    suspend fun save(items: List<MatchItem>, coveredStart: Long, coveredEnd: Long) = mutex.withLock {
        val previous = loadLocked()
        writeJson(
            file,
            Snapshot(
                fetchedAt = nowMillis(),
                items = items,
                homeFetchedAt = previous?.homeFetchedAt ?: 0L,
                homeItems = previous?.homeItems.orEmpty(),
                coveredStart = coveredStart,
                coveredEnd = coveredEnd,
            )
        )
    }

    suspend fun saveHome(items: List<MatchItem>) = mutex.withLock {
        val previous = loadLocked()
        writeJson(
            file,
            Snapshot(
                fetchedAt = previous?.fetchedAt ?: 0L,
                items = previous?.items.orEmpty(),
                homeFetchedAt = nowMillis(),
                homeItems = items,
                coveredStart = previous?.coveredStart ?: 0L,
                coveredEnd = previous?.coveredEnd ?: 0L,
            )
        )
    }

    suspend fun load(): Snapshot? = mutex.withLock { loadLocked() }

    /** 系列赛映射（低频数据）：成功拉取后写入，供离线与冷启动兜底 */
    suspend fun saveSeries(seriesMap: Map<String, String>) = mutex.withLock { writeJson(seriesFile, seriesMap) }

    suspend fun loadSeries(): Map<String, String>? = mutex.withLock { readJson(seriesFile) }

    /**
     * 长缓存 JSON 兜底：键值 + 写入时间统一存在一个文件里，供低频接口冷启动直接命中，
     * 超过 [maxAgeMillis] 视为过期。整个文件在内存中保留一份，避免重复读取。
     */
    suspend fun loadCachedJson(key: String, maxAgeMillis: Long): CachedJson? = mutex.withLock {
        val entry = loadApiCacheLocked().entries[key]
        if (entry == null || nowMillis() - entry.savedAt > maxAgeMillis) {
            null
        } else {
            CachedJson(entry.json, entry.savedAt)
        }
    }

    suspend fun saveCachedJson(key: String, json: String) = mutex.withLock {
        val entries = loadApiCacheLocked().entries.toMutableMap()
        entries[key] = ApiCacheEntry(json = json, savedAt = nowMillis())
        if (entries.size > MAX_API_CACHE_ENTRIES) {
            entries.entries
                .sortedBy { it.value.savedAt }
                .take(entries.size - MAX_API_CACHE_ENTRIES)
                .forEach { entries.remove(it.key) }
        }
        val next = ApiCacheFile(entries)
        apiCache = next
        writeJson(apiCacheFile, next)
    }

    private fun loadApiCacheLocked(): ApiCacheFile =
        apiCache ?: readJson<ApiCacheFile>(apiCacheFile)?.also { apiCache = it } ?: ApiCacheFile()

    private fun loadLocked(): Snapshot? = readJson(file)

    private inline fun <reified T : Any> readJson(path: okio.Path): T? = runCatching {
        if (!fs.exists(path)) return null
        fs.read(path) { readUtf8() }
    }
        .mapCatching { json.decodeFromString<T>(it) }
        .onFailure { logWarn("valo", "快照读取失败", it) }
        .getOrNull()

    private inline fun <reified T> writeJson(path: okio.Path, value: T) {
        runCatching {
            val tmp = path.parent!! / (path.name + ".tmp")
            fs.write(tmp) { writeUtf8(json.encodeToString(value)) }
            // 原子替换失败时退回直接覆盖
            runCatching { fs.atomicMove(tmp, path) }
                .onFailure {
                    fs.write(path) { writeUtf8(json.encodeToString(value)) }
                    fs.delete(tmp)
                }
        }.onFailure { logWarn("valo", "快照写入失败", it) }
    }
}
