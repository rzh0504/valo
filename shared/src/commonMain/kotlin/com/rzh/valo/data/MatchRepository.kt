package com.rzh.valo.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 内存缓存：TTL + 按 key 单飞（同一 key 同时只有一个在途请求，不同 key 互不阻塞），
 * 条目数超上限时淘汰最早拉取的条目。[now] 仅供测试注入时钟。
 */
internal class TtlCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val now: () -> Long = ::nowMillis,
) {

    private class Entry<V>(val data: V, val fetchedAt: Long, val ttl: Long) {
        fun isValid(nowMs: Long): Boolean = nowMs - fetchedAt < ttl
    }

    private val entries = HashMap<K, Entry<V>>()
    private val locks = HashMap<K, Mutex>()
    private val mapMutex = Mutex()

    /** [ttl] 可按值决定有效期（如已结束比赛用长缓存），默认使用构造时的 TTL */
    suspend fun get(
        key: K,
        force: Boolean,
        ttl: (V) -> Long = { ttlMillis },
        fetch: suspend () -> V,
    ): V {
        val keyMutex = mapMutex.withLock { locks.getOrPut(key) { Mutex() } }
        return keyMutex.withLock {
            val cached = mapMutex.withLock { entries[key] }
            if (!force && cached != null && cached.isValid(now())) {
                cached.data
            } else {
                fetch().also { put(key, it, ttl(it)) }
            }
        }
    }

    /** 只读未过期条目，用于接入磁盘兜底 */
    suspend fun peek(key: K): V? = mapMutex.withLock {
        entries[key]?.takeIf { it.isValid(now()) }?.data
    }

    suspend fun put(key: K, value: V, ttl: Long = ttlMillis) {
        mapMutex.withLock {
            entries[key] = Entry(value, now(), ttl)
            if (entries.size > MAX_ENTRIES) {
                entries.entries
                    .sortedBy { it.value.fetchedAt }
                    .take(entries.size - MAX_ENTRIES)
                    .forEach { entries.remove(it.key) }
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 32
    }
}

/**
 * 长缓存：内存 TTL + 磁盘 JSON 兜底，仅用于低频数据。
 * 冷启动先读磁盘可避免整页重新联网；下拉刷新（force）会绕过两者。
 */
private class LongCache<K : Any, V : Any>(
    private val prefix: String,
    private val ttlMillis: Long,
    private val store: SnapshotStore,
    private val keyText: (K) -> String,
    private val encode: (V) -> String,
    private val decode: (String) -> V,
) {
    private val memory = TtlCache<K, V>(ttlMillis)

    suspend fun get(key: K, force: Boolean, fetch: suspend () -> V): V {
        if (!force) {
            memory.peek(key)?.let { return it }
            store.loadCachedJson(diskKey(key), ttlMillis)?.let { cached ->
                // 恢复剩余有效期，避免磁盘命中后把旧数据再续满一个完整 TTL
                val remaining = cached.savedAt + ttlMillis - nowMillis()
                if (remaining > 0) {
                    runCatching { decode(cached.json) }.getOrNull()?.let {
                        memory.put(key, it, remaining)
                        return it
                    }
                }
            }
        }
        val value = memory.get(key, force) { fetch() }
        store.saveCachedJson(diskKey(key), encode(value))
        return value
    }

    private fun diskKey(key: K) = "$prefix/${keyText(key)}"
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
    private val pageCache = TtlCache<Int, MatchListData>(WINDOW_TTL)
    private val tournamentCache = TtlCache<String, List<MatchItem>>(WINDOW_TTL)
    private val fightCache = TtlCache<String, FightHistoryData>(DETAIL_TTL)
    private val mapForesightCache = TtlCache<String, List<MapRecord>>(DETAIL_TTL)
    private val gameMapCache = TtlCache<String, Set<String>>(DAY)
    private val seriesCache = TtlCache<Unit, Map<String, String>>(DAY)

    private val cacheJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    // 低频资料：内存 + 磁盘双缓存，冷启动不再为整页数据联网
    private val teamBaseCache = longCache("team_base", LONG_TTL, TeamBase.serializer())
    private val teamRosterCache = longCache("team_roster", LONG_TTL, TeamRoster.serializer())
    private val teamStatsCache = longCache("team_stats", STATS_TTL, TeamStats.serializer())
    private val teamRecordCache = longCache("team_record", LONG_TTL, TeamRecord.serializer())
    private val teamRewardCache = longCache("team_rewards", LONG_TTL, ListSerializer(TeamReward.serializer()))
    private val playerBaseCache = longCache("player_base", LONG_TTL, PlayerBase.serializer())
    private val playerAchievementCache =
        longCache("player_achievements", LONG_TTL, ListSerializer(PlayerAchievement.serializer()))
    private val playerCareerCache = longCache("player_career", LONG_TTL, ListSerializer(PlayerCareer.serializer()))
    private val playerStatsCache = longCache("player_career_stats", STATS_TTL, PlayerCareerStats.serializer())
    private val playerHeroCache = longCache("player_hero_stats", STATS_TTL, PlayerHeroData.serializer())
    private val stageCache = longCache("tournament_stages", TOURNAMENT_TTL, ListSerializer(TournamentStage.serializer()))
    private val integralCache = LongCache<Pair<String, String>, List<IntegralGroup>>(
        prefix = "tournament_integral",
        ttlMillis = TOURNAMENT_TTL,
        store = store,
        keyText = { "${it.first}_${it.second}" },
        encode = { cacheJson.encodeToString(ListSerializer(IntegralGroup.serializer()), it) },
        decode = { cacheJson.decodeFromString(ListSerializer(IntegralGroup.serializer()), it) },
    )
    private val tournamentListCache = LongCache<Unit, List<TournamentItem>>(
        prefix = "tournament_list",
        ttlMillis = TOURNAMENT_TTL,
        store = store,
        keyText = { "all" },
        encode = { cacheJson.encodeToString(ListSerializer(TournamentItem.serializer()), it) },
        decode = { cacheJson.decodeFromString(ListSerializer(TournamentItem.serializer()), it) },
    )

    private fun <V : Any> longCache(
        prefix: String,
        ttlMillis: Long,
        serializer: KSerializer<V>,
        keyText: (String) -> String = { it },
    ): LongCache<String, V> = LongCache(
        prefix = prefix,
        ttlMillis = ttlMillis,
        store = store,
        keyText = keyText,
        encode = { cacheJson.encodeToString(serializer, it) },
        decode = { cacheJson.decodeFromString(serializer, it) },
    )

    /**
     * 拉取 [startTime, endTime) 时间窗内的比赛（升序）。
     * 缓存有效期内直接返回缓存；[force] 为 true 时强制刷新（如下拉刷新）。
     * 结果条数超过单页上限时按页补齐，最多 [MAX_PAGES] 页。
     */
    suspend fun schedule(startTime: Long, endTime: Long, force: Boolean = false): List<MatchItem> =
        withContext(Dispatchers.Default) {
            windowCache.get(startTime to endTime, force) { fetchPages(startTime, endTime) }
        }

    /**
     * 整站比赛分页，按开始时间倒序（实测 sort=2 为从新到旧，与时间窗无关），
     * 战队赛程页按场数向历史回溯时使用。count 为整站总场数。
     */
    suspend fun recentMatchesPage(page: Int, force: Boolean = false): MatchListData =
        withContext(Dispatchers.Default) {
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
        withContext(Dispatchers.Default) {
            tournamentCache.get(tournamentId, force) { fetchTournamentPages(tournamentId) }
        }

    /** 前瞻 · 历史交手记录，单条缓存 [DETAIL_TTL]。 */
    suspend fun fightHistory(matchId: String, force: Boolean = false): FightHistoryData =
        withContext(Dispatchers.Default) {
            fightCache.get(matchId, force) { api.fightHistory(matchId) }
        }

    /**
     * 前瞻 · 地图胜率：保留当前竞技图池内的全部地图（含暂无交手数据的）。
     * 图池列表拉取失败时退回旧行为（仅显示有交手数据的地图）。
     */
    suspend fun mapForesight(matchId: String, force: Boolean = false): List<MapRecord> =
        withContext(Dispatchers.Default) {
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

    // ---------- 战队详情（低频历史数据，长缓存并持久化） ----------

    suspend fun teamBase(teamId: String, force: Boolean = false): TeamBase =
        withContext(Dispatchers.Default) {
            teamBaseCache.get(teamId, force) { api.teamBase(teamId) }
        }

    suspend fun teamRoster(teamId: String, force: Boolean = false): TeamRoster =
        withContext(Dispatchers.Default) {
            teamRosterCache.get(teamId, force) { api.teamRoster(teamId) }
        }

    suspend fun teamStats(teamId: String, force: Boolean = false): TeamStats =
        withContext(Dispatchers.Default) {
            teamStatsCache.get(teamId, force) { api.teamStats(teamId) }
        }

    suspend fun teamRecord(teamId: String, force: Boolean = false): TeamRecord =
        withContext(Dispatchers.Default) {
            teamRecordCache.get(teamId, force) { api.teamRecord(teamId) }
        }

    /** 全部赛事名次，按时间倒序；详情页前 5 条与「查看全部」页共用同一份缓存。 */
    suspend fun teamRewards(teamId: String, force: Boolean = false): List<TeamReward> =
        withContext(Dispatchers.Default) {
            teamRewardCache.get(teamId, force) { fetchRewards(teamId) }
        }

    /** 全部赛事（tournament/list，开始时间升序全量拉取），单份缓存 [WINDOW_TTL]。 */
    suspend fun tournamentList(force: Boolean = false): List<TournamentItem> =
        withContext(Dispatchers.Default) {
            tournamentListCache.get(Unit, force) { fetchTournaments() }
        }

    /**
     * tournament_id → 系列赛 id 映射（主系列优先、缺失时用子系列，与赛事卡展示名前缀规则一致），
     * 供赛程的系列赛过滤使用：低频数据按天缓存；内存与网络都不可用时回退磁盘快照，仍无快照才抛出。
     */
    suspend fun tournamentSeriesMap(force: Boolean = false): Map<String, String> =
        withContext(Dispatchers.Default) {
            try {
                seriesCache.get(Unit, force) {
                    buildSeriesMap(tournamentList(force)).also { store.saveSeries(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.loadSeries() ?: throw e
            }
        }

    /** 选手基础资料（低频数据，按半天缓存） */
    suspend fun playerBase(playerId: String, force: Boolean = false): PlayerBase =
        withContext(Dispatchers.Default) {
            playerBaseCache.get(playerId, force) { api.playerBase(playerId) }
        }

    /** 选手赛事成绩（一次返回全部，按半天缓存） */
    suspend fun playerAchievements(playerId: String, force: Boolean = false): List<PlayerAchievement> =
        withContext(Dispatchers.Default) {
            playerAchievementCache.get(playerId, force) {
                api.playerAchievements(playerId).list.sortedByDescending { it.date.orEmpty() }
            }
        }

    /** 生涯数据：先从赛事成绩中取 S 级赛事，再汇总总战绩与个人指标 */
    suspend fun playerCareerStats(playerId: String, force: Boolean = false): PlayerCareerStats =
        withContext(Dispatchers.Default) {
            playerStatsCache.get(playerId, force) {
                val sTournamentIds = playerAchievements(playerId, force)
                    .mapNotNull { it.tournament }
                    .filter { it.level == "S" && it.id.isNotBlank() }
                    .map { it.id }
                    .distinct()
                val totals = api.playerTopStats(playerId)
                val stats = if (sTournamentIds.isEmpty()) {
                    null
                } else {
                    runCatching { api.playerStats(playerId, sTournamentIds) }.getOrNull()
                }
                PlayerCareerStats(
                    matchCount = totals.matchCount,
                    matchWinCount = totals.matchWinCount,
                    roundCount = totals.roundCount,
                    roundWinCount = totals.roundWinCount,
                    kills = stats?.kills ?: 0,
                    deaths = stats?.deaths ?: 0,
                    assists = stats?.assists ?: 0,
                    maxKills = stats?.maxKills ?: 0,
                    acs = stats?.acs,
                    adr = stats?.adr,
                    kd = stats?.kd,
                    kda = stats?.kda,
                    kast = stats?.kast,
                )
            }
        }

    /** 常用特工（近三个月，按使用回合降序） */
    suspend fun playerHeroStats(playerId: String, force: Boolean = false): PlayerHeroData =
        withContext(Dispatchers.Default) {
            playerHeroCache.get(playerId, force) {
                api.playerHeroData(playerId).let { data ->
                    data.copy(heroes = data.heroes.sortedByDescending { it.rounds })
                }
            }
        }

    /** 选手职业生涯：只保留选手身份，按入队时间倒序 */
    suspend fun playerCareer(playerId: String, force: Boolean = false): List<PlayerCareer> =
        withContext(Dispatchers.Default) {
            playerCareerCache.get(playerId, force) {
                api.playerCareer(playerId)
                    .filter { it.careerType == 1 }
                    .sortedByDescending { it.startDate.orEmpty() }
            }
        }

    /** 仅读磁盘快照的映射（不发网络）：小组件等后台场景优先用，避免为算过滤而联网 */
    suspend fun cachedSeriesMap(): Map<String, String>? = store.loadSeries()

    /** 赛事阶段与分组，单赛事缓存 [WINDOW_TTL]。 */
    suspend fun tournamentStages(tournamentId: String, force: Boolean = false): List<TournamentStage> =
        withContext(Dispatchers.Default) {
            stageCache.get(tournamentId, force) { api.tournamentStages(tournamentId) }
        }

    /** 阶段积分榜，按（赛事, 阶段）缓存 [WINDOW_TTL]。 */
    suspend fun tournamentIntegral(
        tournamentId: String,
        stageId: String,
        force: Boolean = false,
    ): List<IntegralGroup> =
        withContext(Dispatchers.Default) {
            integralCache.get(tournamentId to stageId, force) {
                api.tournamentIntegral(tournamentId, stageId)
            }
        }

    /**
     * 比赛详情：已结束的比赛不再变化，缓存 [FINISHED_TTL] 并落盘；
     * 未结束的保持 [DETAIL_TTL]，避免比分与状态展示过旧。
     */
    suspend fun matchDetail(matchId: String, force: Boolean = false): MatchItem =
        withContext(Dispatchers.Default) {
            if (!force) {
                detailCache.peek(matchId)?.let { return@withContext it }
                store.loadCachedJson("match_detail/$matchId", FINISHED_TTL)?.let { cached ->
                    val remaining = cached.savedAt + FINISHED_TTL - nowMillis()
                    if (remaining > 0) {
                        runCatching {
                            cacheJson.decodeFromString(MatchItem.serializer(), cached.json)
                        }.getOrNull()?.let {
                            detailCache.put(matchId, it, remaining)
                            return@withContext it
                        }
                    }
                }
            }
            val item = detailCache.get(
                matchId,
                force,
                ttl = { if (it.status == MatchStatus.FINISHED) FINISHED_TTL else DETAIL_TTL },
            ) { api.battleDetail(matchId) }
            if (item.status == MatchStatus.FINISHED) {
                store.saveCachedJson(
                    "match_detail/$matchId",
                    cacheJson.encodeToString(MatchItem.serializer(), item),
                )
            }
            item
        }

    /** 逐回合地图明细（未开赛时为空数据），单条缓存 [DETAIL_TTL]。 */
    suspend fun matchRound(matchId: String, force: Boolean = false): RoundData =
        withContext(Dispatchers.Default) {
            roundCache.get(matchId, force) { api.roundData(matchId) }
        }

    /** 详情确认已结束后，把逐回合数据提升为长缓存（体积较大，仅内存） */
    suspend fun cacheFinishedRound(matchId: String, round: RoundData) {
        if (round.list.isNotEmpty()) roundCache.put(matchId, round, FINISHED_TTL)
    }

    /** 保存/读取快照（仅默认时间窗的数据会写入，供小组件使用）；[coveredStart, coveredEnd) 为数据实际覆盖的时间窗。 */
    suspend fun saveSnapshot(items: List<MatchItem>, coveredStart: Long, coveredEnd: Long) =
        store.save(items, coveredStart, coveredEnd)

    suspend fun loadSnapshot(): SnapshotStore.Snapshot? = store.load()

    /**
     * 冷启动时从磁盘快照中恢复指定时间窗，网络请求随后负责更新数据。
     * 快照必须完整覆盖 [startTime, endTime)，否则视为未命中——
     * 小组件任务写入的窗口（近 36 小时 + 7 天）比赛程页默认窗口窄，
     * 直接按窗口过滤会把"窗口外"错当成"没有比赛"。
     */
    suspend fun cachedSchedule(startTime: Long, endTime: Long): List<MatchItem> =
        withContext(Dispatchers.Default) {
            val snapshot = store.load() ?: return@withContext emptyList()
            if (nowMillis() - snapshot.fetchedAt > SNAPSHOT_TTL) return@withContext emptyList()
            if (startTime < snapshot.coveredStart || endTime > snapshot.coveredEnd) return@withContext emptyList()
            snapshot.items.filter { it.startTime in startTime until endTime }.sortedBy { it.startTime }
        }

    /**
     * 首页快照仅作短时间内重进的冷启动兑底：「今天」页展示实时比分/状态，
     * 过期阈值与接口层缓存 [WINDOW_TTL] 一致，避免把几小时前的旧状态当作当前状态展示。
     */
    suspend fun cachedHomeSchedule(startTime: Long, endTime: Long): List<MatchItem> =
        withContext(Dispatchers.Default) {
            val snapshot = store.load() ?: return@withContext emptyList()
            if (nowMillis() - snapshot.homeFetchedAt > HOME_SNAPSHOT_TTL) return@withContext emptyList()
            snapshot.homeItems.filter { it.startTime in startTime until endTime }.sortedBy { it.startTime }
        }

    suspend fun saveHomeSnapshot(items: List<MatchItem>) = withContext(Dispatchers.Default) {
        store.saveHome(items)
    }

    /** 小组件刷新窗口：近 36 小时 + 未来 7 天。 */
    fun widgetWindow(): Pair<Long, Long> {
        val now = nowMillis()
        return (now - 36 * HOUR) to (now + 7 * DAY)
    }

    /** 实测服务端单页上限 500（page_size 传 999 也只返回 500 条），按页补齐全量 */
    private suspend fun fetchTournaments(): List<TournamentItem> {
        val all = ArrayList<TournamentItem>()
        var page = 1
        var count = Int.MAX_VALUE
        while (page <= MAX_PAGES && all.size < count) {
            val data = api.tournamentList(
                TournamentListRequest(
                    gameId = HaojiaoApi.GAME_ID,
                    page = page,
                    pageSize = TOURNAMENT_PAGE_SIZE,
                    startTimeSort = "asc",
                )
            )
            count = data.count
            all += data.list
            if (data.list.isEmpty()) break
            page++
        }
        return all.distinctBy { it.id }
    }

    private suspend fun fetchRewards(teamId: String): List<TeamReward> {
        val all = ArrayList<TeamReward>()
        var page = 1
        var count = Int.MAX_VALUE
        while (page <= MAX_PAGES && all.size < count) {
            val data = api.teamRewards(teamId, page, TOURNAMENT_PAGE_SIZE)
            count = data.count
            all += data.list
            if (data.list.size < TOURNAMENT_PAGE_SIZE) break
            page++
        }
        return all
            .distinctBy { it.id.ifBlank { "${it.group?.id}_${it.rewardTime}" } }
            .sortedByDescending { it.rewardTime }
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
        /** 已结束比赛与历史资料不再变化，缓存一周并持久化 */
        private const val FINISHED_TTL = 7 * DAY
        private const val LONG_TTL = 7 * DAY
        /** 随赛程推进缓慢变化的统计（战队/选手数据、特工数据） */
        private const val STATS_TTL = DAY
        /** 赛事列表、阶段与积分榜 */
        private const val TOURNAMENT_TTL = 6 * HOUR
        private const val SNAPSHOT_TTL = 24 * 60 * 60_000L
        private const val HOME_SNAPSHOT_TTL = WINDOW_TTL
        // internal：页面层的翻页结束判断（不足一页即最后一页）与它保持一致
        internal const val PAGE_SIZE = 100
        private const val MAX_PAGES = 3
        private const val TOURNAMENT_PAGE_SIZE = 500
        private const val SORT_ASC = 1
        /** 倒序（2026-09-27 实测：第 1 页返回最新比赛） */
        private const val SORT_DESC = 2

        private const val GAME_MAPS_KEY = "game_maps"
    }
}

/** tournament_id → 系列赛 id 映射（series_info 优先、second_series_info 兜底）；
 * 无系列与空 id 的赛事不进映射，对应的比赛在系列过滤中始终显示。 */
internal fun buildSeriesMap(items: List<TournamentItem>): Map<String, String> {
    val map = HashMap<String, String>()
    for (item in items) {
        val info = item.series ?: item.secondSeries ?: continue
        if (info.id.isBlank()) continue
        map[item.id] = info.id
    }
    return map
}
