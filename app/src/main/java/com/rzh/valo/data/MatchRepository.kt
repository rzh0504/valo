package com.rzh.valo.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 内存缓存：TTL + 按 key 单飞（同一 key 同时只有一个在途请求，不同 key 互不阻塞），
 * 条目数超上限时淘汰最早拉取的条目。[now] 仅供测试注入时钟。
 */
internal class TtlCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private class Entry<V>(val data: V, val fetchedAt: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val locks = ConcurrentHashMap<K, Mutex>()

    suspend fun get(key: K, force: Boolean, fetch: suspend () -> V): V =
        locks.getOrPut(key) { Mutex() }.withLock {
            val cached = entries[key]
            val nowMs = now()
            if (!force && cached != null && nowMs - cached.fetchedAt < ttlMillis) {
                cached.data
            } else {
                fetch().also { put(key, it, nowMs) }
            }
        }

    private fun put(key: K, value: V, nowMs: Long) {
        entries[key] = Entry(value, nowMs)
        if (entries.size > MAX_ENTRIES) {
            entries.entries
                .sortedBy { it.value.fetchedAt }
                .take(entries.size - MAX_ENTRIES)
                .forEach { entries.remove(it.key) }
        }
    }

    companion object {
        const val MAX_ENTRIES = 32
    }
}

/**
 * 数据仓库：内存缓存 + TTL + 单飞（同一时间窗/比赛同时只允许一个在途请求），
 * 保证对 API 的请求频率控制在正常浏览量级。
 */
class MatchRepository(
    private val api: HaojiaoApi,
    private val store: SnapshotStore,
) {

    private val windowCache = TtlCache<Pair<Long, Long>, List<MatchItem>>(WINDOW_TTL)
    private val detailCache = TtlCache<String, MatchItem>(DETAIL_TTL)
    private val roundCache = TtlCache<String, RoundData>(DETAIL_TTL)
    private val recentCache = TtlCache<String, RecentBigMatchData>(DETAIL_TTL)
    private val pageCache = TtlCache<Int, MatchListData>(WINDOW_TTL)
    private val tournamentCache = TtlCache<String, List<MatchItem>>(WINDOW_TTL)
    private val fightCache = TtlCache<String, FightHistoryData>(DETAIL_TTL)
    private val mapForesightCache = TtlCache<String, List<MapRecord>>(DETAIL_TTL)
    private val gameMapCache = TtlCache<String, Set<String>>(DAY)
    private val stageCache = TtlCache<String, List<TournamentStage>>(WINDOW_TTL)
    private val integralCache = TtlCache<Pair<String, String>, List<IntegralGroup>>(WINDOW_TTL)

    /**
     * 拉取 [startTime, endTime) 时间窗内的比赛（升序）。
     * 缓存有效期内直接返回缓存；[force] 为 true 时强制刷新（如下拉刷新）。
     * 结果条数超过单页上限时按页补齐，最多 [MAX_PAGES] 页。
     */
    suspend fun schedule(startTime: Long, endTime: Long, force: Boolean = false): List<MatchItem> =
        withContext(Dispatchers.IO) {
            windowCache.get(startTime to endTime, force) { fetchPages(startTime, endTime) }
        }

    /**
     * 整站比赛分页，按开始时间倒序（实测 sort=2 为从新到旧，与时间窗无关），
     * 战队赛程页按场数向历史回溯时使用。count 为整站总场数。
     */
    suspend fun recentMatchesPage(page: Int, force: Boolean = false): MatchListData =
        withContext(Dispatchers.IO) {
            pageCache.get(page, force) {
                api.matchList(
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
                        sortByStartTime = SORT_DESC,
                    )
                )
            }
        }

    /** 某赛事（tournament_id）的全部比赛，升序返回。 */
    suspend fun tournamentMatches(tournamentId: String, force: Boolean = false): List<MatchItem> =
        withContext(Dispatchers.IO) {
            tournamentCache.get(tournamentId, force) { fetchTournamentPages(tournamentId) }
        }

    /** 前瞻 · 历史交手记录，单条缓存 [DETAIL_TTL]。 */
    suspend fun fightHistory(matchId: String, force: Boolean = false): FightHistoryData =
        withContext(Dispatchers.IO) {
            fightCache.get(matchId, force) { api.fightHistory(matchId) }
        }

    /**
     * 前瞻 · 地图胜率：保留当前竞技图池内的全部地图（含暂无交手数据的）。
     * 图池列表拉取失败时退回旧行为（仅显示有交手数据的地图）。
     */
    suspend fun mapForesight(matchId: String, force: Boolean = false): List<MapRecord> =
        withContext(Dispatchers.IO) {
            mapForesightCache.get(matchId, force) {
                val records = api.mapForesight(matchId)
                val rotation = runCatching { rotationMapIds(force) }.getOrNull()
                if (rotation == null) records.filter { it.hasData } else records.filter { it.id in rotation }
            }
        }

    /** 当前竞技图池（map_status=1）的地图 id 集合，按 [DAY] 缓存。 */
    private suspend fun rotationMapIds(force: Boolean): Set<String> =
        gameMapCache.get(GAME_MAPS_KEY, force) {
            api.valorantMaps().filter { it.status == 1 }.map { it.id }.toSet()
        }

    /** 赛事阶段与分组，单赛事缓存 [WINDOW_TTL]。 */
    suspend fun tournamentStages(tournamentId: String, force: Boolean = false): List<TournamentStage> =
        withContext(Dispatchers.IO) {
            stageCache.get(tournamentId, force) { api.tournamentStages(tournamentId) }
        }

    /** 阶段积分榜，按（赛事, 阶段）缓存 [WINDOW_TTL]。 */
    suspend fun tournamentIntegral(
        tournamentId: String,
        stageId: String,
        force: Boolean = false,
    ): List<IntegralGroup> =
        withContext(Dispatchers.IO) {
            integralCache.get(tournamentId to stageId, force) {
                api.tournamentIntegral(tournamentId, stageId)
            }
        }

    /** 比赛详情，单条缓存 [DETAIL_TTL]。 */
    suspend fun matchDetail(matchId: String, force: Boolean = false): MatchItem =
        withContext(Dispatchers.IO) {
            detailCache.get(matchId, force) { api.battleDetail(matchId) }
        }

    /** 逐回合地图明细（未开赛时为空数据），单条缓存 [DETAIL_TTL]。 */
    suspend fun matchRound(matchId: String, force: Boolean = false): RoundData =
        withContext(Dispatchers.IO) {
            roundCache.get(matchId, force) { api.roundData(matchId) }
        }

    /** 双方近期大赛表现（前瞻数据），单条缓存 [DETAIL_TTL]。 */
    suspend fun matchRecent(matchId: String, force: Boolean = false): RecentBigMatchData =
        withContext(Dispatchers.IO) {
            recentCache.get(matchId, force) { api.recentBigMatch(matchId) }
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

    private suspend fun fetchTournamentPages(tournamentId: String): List<MatchItem> {
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
                    tournamentId = tournamentId,
                )
            )
            count = data.count
            all += data.list
            if (data.list.isEmpty()) break
            page++
        }
        return all.distinctBy { it.id }.sortedBy { it.startTime }
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
        // internal：页面层回前台的再验证间隔与它保持一致
        internal const val WINDOW_TTL = 5 * 60_000L
        private const val DETAIL_TTL = 15 * 60_000L
        private const val SNAPSHOT_TTL = 24 * 60 * 60_000L
        private const val HOME_SNAPSHOT_TTL = WINDOW_TTL
        // internal：页面层的翻页结束判断（不足一页即最后一页）与它保持一致
        internal const val PAGE_SIZE = 100
        private const val MAX_PAGES = 3
        private const val SORT_ASC = 1
        /** 倒序（2026-09-27 实测：第 1 页返回最新比赛） */
        private const val SORT_DESC = 2

        private const val GAME_MAPS_KEY = "game_maps"
    }
}
