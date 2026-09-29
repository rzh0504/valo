package com.rzh.valo.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class ApiException(message: String) : Exception(message)

/**
 * 号角（web.haojiao.cc）无畏契约分区 API 客户端。
 *
 * 请求需携带 5 个自定义头，签名为 SHA1(SALT + nonce + timestamp)；
 * Content-Type 为 text/plain 的响应是 AES-192-CBC 加密的 Base64 文本，
 * key 固定 24 字节，IV 取 key 前 16 字节，PKCS7 填充。
 */
class HaojiaoApi {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client: HttpClient = HttpClient {
        config()
    }

    /** 引擎由各平台 source set 提供（Android OkHttp / iOS Darwin），公共配置集中在这里 */
    private fun HttpClientConfig<*>.config() {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    /** 比赛列表（POST，按时间窗口 [startTime, endTime) 过滤） */
    suspend fun matchList(request: MatchListRequest): MatchListData =
        exec(jsonRequest(HttpMethod.Post, "$BASE/wiki/api/v1/match/list_visitor", json.encodeToString(request)))

    /** 比赛总览（data 为 {radar_config, match, additional, game}，取其中 match） */
    suspend fun battleDetail(matchId: String): MatchItem {
        val data: BattleDetailData = exec(
            get("$BASE/wiki/api/v1/match/battle_detail") {
                parameters.append("match_id", matchId)
                parameters.append("platform", "web")
            }
        )
        return data.match ?: throw ApiException("响应数据为空")
    }

    /** 逐回合明细：每张地图的小局比分、攻防半场、回合序列与选手数据 */
    suspend fun roundData(matchId: String): RoundData =
        exec(get("$BASE/wiki/api/v1/match/get_valorant_round") { parameters.append("match_id", matchId) })

    /** 前瞻 · 历史交手记录：两队 H2H 胜负统计与交手比赛列表 */
    suspend fun fightHistory(matchId: String): FightHistoryData =
        exec(get("$BASE/wiki/api/v1/foresight/fight_big_match") { parameters.append("match_id", matchId) })

    /** 前瞻 · 地图胜率：双方在各地图的历史场次与胜场（返回全部地图，按当前图池过滤由仓库层处理） */
    suspend fun mapForesight(matchId: String): List<MapRecord> =
        exec(get("$BASE/wiki/api/v1/foresight/valorant_map") { parameters.append("match_id", matchId) })

    /** 全地图列表（map_status=1 为当前竞技图池） */
    suspend fun valorantMaps(): List<GameMap> =
        exec(get("$BASE/wiki/api/v1/game/valorant_map") { })

    /** 赛事阶段与分组（stage_id 供积分表使用） */
    suspend fun tournamentStages(tournamentId: String): List<TournamentStage> =
        exec(
            get("$BASE/wiki/api/v1/tournament/stage_with_group") {
                parameters.append("game_id", GAME_ID)
                parameters.append("platform", "web")
                parameters.append("tournament_id", tournamentId)
            }
        )

    /** 阶段积分榜（按分组返回，rows 顺序即排名） */
    suspend fun tournamentIntegral(tournamentId: String, stageId: String): List<IntegralGroup> =
        exec(
            get("$BASE/wiki/api/v1/tournament/integral/table/v2") {
                parameters.append("tournament_id", tournamentId)
                parameters.append("stage_id", stageId)
            }
        )

    /** 战队基础信息（participant_id 即 team_id） */
    suspend fun teamBase(teamId: String): TeamBase =
        exec(
            get("$BASE/wiki/api/v1/team/base") {
                parameters.append("team_id", teamId)
                parameters.append("platform", "web")
            }
        )

    /** 战队名单：现役 / 前成员 / 经理教练组 */
    suspend fun teamRoster(teamId: String): TeamRoster =
        exec(
            get("$BASE/wiki/api/v1/team/roster") {
                parameters.append("team_id", teamId)
                parameters.append("platform", "web")
            }
        )

    /** 战队近期数据：场次胜场、地图局胜场、逐图胜率与英雄池（full=false 约 2026 年内） */
    suspend fun teamStats(teamId: String): TeamStats =
        exec(
            get("$BASE/wiki/api/v1/team/get_stats") {
                parameters.append("team_id", teamId)
                parameters.append("full", "false")
            }
        )

    /** 全队史总战绩（POST） */
    suspend fun teamRecord(teamId: String): TeamRecord =
        exec(jsonRequest(HttpMethod.Post, "$BASE/wiki/api/v1/tournament/get_team_record", json.encodeToString(TeamStatsRequest(teamId, GAME_ID))))

    /** 选手数据：队史选手的英雄池与 ACS/KD/ADR/KAST 等个人数据（POST） */
    suspend fun playerTeamStats(teamId: String): List<PlayerStatsRow> =
        exec(jsonRequest(HttpMethod.Post, "$BASE/wiki/api/v1/tournament/get_valorant_stats", json.encodeToString(TeamStatsRequest(teamId, GAME_ID))))

    /** 赛事名次与奖金 */
    suspend fun teamReward(teamId: String): List<TeamReward> =
        exec<TeamRewardData>(
            get("$BASE/wiki/api/v1/team/reward") {
                parameters.append("team_id", teamId)
                parameters.append("full", "false")
                parameters.append("page", "1")
                parameters.append("page_size", "10")
                parameters.append("platform", "web")
            }
        ).let { data -> data.list }

    // ---------- 内部：请求构造 + 签名头 + 解密 + 信封解析 ----------

    private fun get(urlString: String, urlBlock: URLBuilder.() -> Unit = {}): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url(urlString)
            url(urlBlock)
        }

    private fun jsonRequest(method: HttpMethod, urlString: String, body: String): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url(urlString)
            this.method = method
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend inline fun <reified T> exec(builder: HttpRequestBuilder): T {
        val ts = Clock.System.now().toEpochMilliseconds().toString()
        val nonce = buildString { repeat(NONCE_LEN) { append(NONCE_CHARS.random()) } }
        val sign = sha1Hex(SALT + nonce + ts)
        builder.header(HEADERS_TIMESTAMP, ts)
        builder.header(HEADERS_NONCE, nonce)
        builder.header(HEADERS_OS, "web")
        builder.header(HEADERS_SIGN, sign)
        builder.header(HEADERS_VERSION, VERSION)

        val response: HttpResponse = client.request(builder)
        val status = response.status
        val raw = response.bodyAsText()
        if (!status.isSuccess()) throw ApiException("HTTP ${status.value}")
        val contentTypeName = response.headers[HttpHeaders.ContentType]
        val text = if (contentTypeName?.contains("text/plain", ignoreCase = true) == true) decrypt(raw) else raw
        val envelope = json.decodeFromString<ApiEnvelope<T>>(text)
        if (envelope.code != 200) throw ApiException("业务码 ${envelope.code}")
        return envelope.data ?: throw ApiException("响应数据为空")
    }

    private fun decrypt(base64Text: String): String {
        val cipherText = decodeBase64(base64Text)
        return aesCbcDecrypt(KEY, cipherText).decodeToString()
    }

    companion object {
        const val BASE = "https://api.haojiao.cc"
        /** 无畏契约分区 */
        const val GAME_ID = "t2Ud5pOQlscKLbRC"
        const val VERSION = "1.52.159"

        private const val HEADERS_TIMESTAMP = "x-hj-timestamp"
        private const val HEADERS_NONCE = "x-hj-nonce"
        private const val HEADERS_OS = "x-hj-os"
        private const val HEADERS_SIGN = "x-hj-sign"
        private const val HEADERS_VERSION = "x-hj-version"

        private const val SALT = "N61P#=Pf\$yz=fwFZa)U8"
        private val KEY = "B-:9bzB8K%~q{Au?^>Pfl*)k".encodeToByteArray()
        private const val NONCE_LEN = 10
        private const val NONCE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

        /** 相对路径 → 完整图片地址（图片资源在 files 域名下） */
        fun imageUrl(path: String?): String? = when {
            path.isNullOrBlank() -> null
            path.startsWith("http") -> path
            else -> "https://files.haojiao.cc$path"
        }
    }
}
