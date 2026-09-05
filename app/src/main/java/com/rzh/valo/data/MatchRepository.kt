package com.rzh.valo.data

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 数据仓库：内存缓存 + TTL + 单飞（同一时间窗只允许一个在途请求），
 * 保证对 API 的请求频率控制在正常浏览量级。
 */
class MatchRepository(
    private val api: HaojiaoApi,
    private val store: SnapshotStore,
) {

    private class Entry(val data: Any, val fetchedAt: Long)

    private val windowCache = ConcurrentHashMap<Pair<Long, Long>, Entry>()
    private val detailCache = ConcurrentHashMap<String, Entry>()
    private val roundCache = ConcurrentHashMap<String, Entry>()
    private val recentCache = ConcurrentHashMap<String, Entry>()
    private val windowMutex = Mutex()
    private val detailMutex = Mutex()
    private val roundMutex = Mutex()
    private val recentMutex = Mutex()

    /**
     * 拉取 [startTime, endTime) 时间窗内的比赛（升序）。
     * 缓存有效期 [WINDOW_TTL] 内直接返回缓存；[force] 为 true 时强制刷新（如下拉刷新）。
     * 结果条数超过单页上限时按页补齐，最多 [MAX_PAGES] 页。
     */
    suspend fun schedule(startTime: Long, endTime: Long, force: Boolean = false): List<MatchItem> =
        withContext(Dispatchers.IO) {
            windowMutex.withLock {
                val key = startTime to endTime
                val cached = windowCache[key]
                val now = System.currentTimeMillis()
                if (!force && cached != null && now - cached.fetchedAt < WINDOW_TTL) {
                    @Suppress("UNCHECKED_CAST")
                    cached.data as List<MatchItem>
                } else {
                    val items = fetchPages(startTime, endTime)
                    windowCache[key] = Entry(items, now)
                    items
                }
            }
        }

    /** 比赛详情，单条缓存 [DETAIL_TTL]。 */
    suspend fun matchDetail(matchId: String, force: Boolean = false): MatchItem =
        withContext(Dispatchers.IO) {
            detailMutex.withLock {
                val cached = detailCache[matchId]
                val now = System.currentTimeMillis()
                if (!force && cached != null && now - cached.fetchedAt < DETAIL_TTL) {
                    cached.data as MatchItem
                } else {
                    val item = api.battleDetail(matchId)
                    detailCache[matchId] = Entry(item, now)
                    item
                }
            }
        }

    /** 逐回合地图明细（未开赛时为空数据），单条缓存 [DETAIL_TTL]。 */
    suspend fun matchRound(matchId: String, force: Boolean = false): RoundData =
        withContext(Dispatchers.IO) {
            roundMutex.withLock {
                val cached = roundCache[matchId]
                val now = System.currentTimeMillis()
                if (!force && cached != null && now - cached.fetchedAt < DETAIL_TTL) {
                    cached.data as RoundData
                } else {
                    val data = api.roundData(matchId)
                    roundCache[matchId] = Entry(data, now)
                    data
                }
            }
        }

    /** 双方近期大赛表现（前瞻数据），单条缓存 [DETAIL_TTL]。 */
    suspend fun matchRecent(matchId: String, force: Boolean = false): RecentBigMatchData =
        withContext(Dispatchers.IO) {
            recentMutex.withLock {
                val cached = recentCache[matchId]
                val now = System.currentTimeMillis()
                if (!force && cached != null && now - cached.fetchedAt < DETAIL_TTL) {
                    cached.data as RecentBigMatchData
                } else {
                    val data = api.recentBigMatch(matchId)
                    recentCache[matchId] = Entry(data, now)
                    data
                }
            }
        }

    /** 保存/读取快照（仅默认时间窗的数据会写入，供小组件使用）；[coveredStart, coveredEnd) 为数据实际覆盖的时间窗。 */
    fun saveSnapshot(items: List<MatchItem>, coveredStart: Long, coveredEnd: Long) =
        store.save(items, coveredStart, coveredEnd)

    fun loadSnapshot(): SnapshotStore.Snapshot? = store.load()

    /**
     * 冷启动时从磁盘快照中恢复指定时间窗，网络请求随后负责更新数据。
     * 快照必须完整覆盖 [startTime, endTime)，否则视为未命中——
     * 小组件任务写入的窗口（近 36 小时 + 7 天）比赛程页默认窗口窄，
     * 直接按窗口过滤会把"窗口外"错当成"没有比赛"。
     */
    suspend fun cachedSchedule(startTime: Long, endTime: Long): List<MatchItem> =
        withContext(Dispatchers.IO) {
            val snapshot = store.load() ?: return@withContext emptyList()
            if (System.currentTimeMillis() - snapshot.fetchedAt > SNAPSHOT_TTL) return@withContext emptyList()
            if (startTime < snapshot.coveredStart || endTime > snapshot.coveredEnd) return@withContext emptyList()
            snapshot.items.filter { it.startTime in startTime until endTime }.sortedBy { it.startTime }
        }

    /**
     * 首页快照仅作短时间内重进的冷启动兑底：「今天」页展示实时比分/状态，
     * 过期阈值与接口层缓存 [WINDOW_TTL] 一致，避免把几小时前的旧状态当作当前状态展示。
     */
    suspend fun cachedHomeSchedule(startTime: Long, endTime: Long): List<MatchItem> =
        withContext(Dispatchers.IO) {
            val snapshot = store.load() ?: return@withContext emptyList()
            if (System.currentTimeMillis() - snapshot.homeFetchedAt > HOME_SNAPSHOT_TTL) return@withContext emptyList()
            snapshot.homeItems.filter { it.startTime in startTime until endTime }.sortedBy { it.startTime }
        }

    suspend fun saveHomeSnapshot(items: List<MatchItem>) = withContext(Dispatchers.IO) {
        store.saveHome(items)
    }

    /** 小组件刷新窗口：近 36 小时 + 未来 7 天。 */
    fun widgetWindow(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return (now - 36 * HOUR) to (now + 7 * DAY)
    }

    private suspend fun fetchPages(startTime: Long, endTime: Long): List<MatchItem> {
        val all = ArrayList<MatchItem>()
        var page = 1
        var count = Int.MAX_VALUE
        while (page <= MAX_PAGES && all.size < count) {
            val data = api.matchList(
                MatchListRequest(
                    gameId = HaojiaoApi.GAME_ID,
                    page = page,
                    pageSize = PAGE_SIZE,
                    platform = "web",
                    matchStatus = listOf(
                        MatchStatus.SCHEDULED,
                        MatchStatus.LIVE,
                        MatchStatus.FINISHED,
                    ),
                    sortByStartTime = SORT_ASC,
                    startTime = startTime,
                    endTime = endTime,
                )
            )
            count = data.count
            all += data.list
            if (data.list.isEmpty()) break
            page++
        }
        return all.distinctBy { it.id }.sortedBy { it.startTime }
    }

    companion object {
        private const val HOUR = 3600_000L
        private const val DAY = 24 * HOUR
        private const val WINDOW_TTL = 5 * 60_000L
        private const val DETAIL_TTL = 15 * 60_000L
        private const val SNAPSHOT_TTL = 24 * 60 * 60_000L
        private const val HOME_SNAPSHOT_TTL = WINDOW_TTL
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 3
        private const val SORT_ASC = 1
    }
}
