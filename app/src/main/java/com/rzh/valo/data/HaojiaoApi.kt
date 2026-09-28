package com.rzh.valo.data

import android.util.Base64
import java.security.MessageDigest
import java.time.Duration
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

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

    private val signInterceptor = Interceptor { chain ->
        val ts = System.currentTimeMillis().toString()
        val nonce = buildString { repeat(NONCE_LEN) { append(NONCE_CHARS.random()) } }
        val sign = MessageDigest.getInstance("SHA-1")
            .digest((SALT + nonce + ts).toByteArray())
            .joinToString("") { "%02x".format(it) }
        chain.proceed(
            chain.request().newBuilder()
                .header("x-hj-timestamp", ts)
                .header("x-hj-nonce", nonce)
                .header("x-hj-os", "web")
                .header("x-hj-sign", sign)
                .header("x-hj-version", VERSION)
                .build()
        )
    }

    private val decryptInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val contentType = response.header("Content-Type").orEmpty()
        if (!response.isSuccessful || !contentType.contains("text/plain", ignoreCase = true)) {
            return@Interceptor response
        }
        val plain = decrypt(response.body?.string().orEmpty())
        response.newBuilder()
            .body(plain.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(signInterceptor)
        .addInterceptor(decryptInterceptor)
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(20))
        .build()

    /** 比赛列表（POST，按时间窗口 [startTime, endTime) 过滤） */
    fun matchList(request: MatchListRequest): MatchListData {
        val body = json.encodeToString(request)
        val httpRequest = Request.Builder()
            .url("$BASE/wiki/api/v1/match/list_visitor")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return exec(httpRequest)
    }

    /** 比赛总览（data 为 {radar_config, match, additional, game}，取其中 match） */
    fun battleDetail(matchId: String): MatchItem {
        val url = "$BASE/wiki/api/v1/match/battle_detail".toHttpUrl().newBuilder()
            .addQueryParameter("match_id", matchId)
            .addQueryParameter("platform", "web")
            .build()
        val data: BattleDetailData = exec(Request.Builder().url(url).build())
        return data.match ?: throw ApiException("响应数据为空")
    }

    /** 逐回合明细：每张地图的小局比分、攻防半场、回合序列与选手数据 */
    fun roundData(matchId: String): RoundData {
        val url = "$BASE/wiki/api/v1/match/get_valorant_round".toHttpUrl().newBuilder()
            .addQueryParameter("match_id", matchId)
            .build()
        return exec(Request.Builder().url(url).build())
    }

    /** 前瞻 · 近期大赛表现：双方各自的近 5 场大型赛事与胜负统计（已结束/未开始的比赛均可查） */
    fun recentBigMatch(matchId: String): RecentBigMatchData {
        val url = "$BASE/wiki/api/v1/foresight/recent_big_match".toHttpUrl().newBuilder()
            .addQueryParameter("match_id", matchId)
            .build()
        return exec(Request.Builder().url(url).build())
    }

    /** 前瞻 · 历史交手记录：两队 H2H 胜负统计与交手比赛列表 */
    fun fightHistory(matchId: String): FightHistoryData {
        val url = "$BASE/wiki/api/v1/foresight/fight_big_match".toHttpUrl().newBuilder()
            .addQueryParameter("match_id", matchId)
            .build()
        return exec(Request.Builder().url(url).build())
    }

    /** 前瞻 · 地图胜率：双方在各地图的历史场次与胜场（返回全部地图，按当前图池过滤由仓库层处理） */
    fun mapForesight(matchId: String): List<MapRecord> {
        val url = "$BASE/wiki/api/v1/foresight/valorant_map".toHttpUrl().newBuilder()
            .addQueryParameter("match_id", matchId)
            .build()
        return exec(Request.Builder().url(url).build())
    }

    /** 全地图列表（map_status=1 为当前竞技图池） */
    fun valorantMaps(): List<GameMap> =
        exec(Request.Builder().url("$BASE/wiki/api/v1/game/valorant_map").build())

    /** 赛事阶段与分组（stage_id 供积分表使用） */
    fun tournamentStages(tournamentId: String): List<TournamentStage> {
        val url = "$BASE/wiki/api/v1/tournament/stage_with_group".toHttpUrl().newBuilder()
            .addQueryParameter("game_id", GAME_ID)
            .addQueryParameter("platform", "web")
            .addQueryParameter("tournament_id", tournamentId)
            .build()
        return exec(Request.Builder().url(url).build())
    }

    /** 阶段积分榜（按分组返回，rows 顺序即排名） */
    fun tournamentIntegral(tournamentId: String, stageId: String): List<IntegralGroup> {
        val url = "$BASE/wiki/api/v1/tournament/integral/table/v2".toHttpUrl().newBuilder()
            .addQueryParameter("tournament_id", tournamentId)
            .addQueryParameter("stage_id", stageId)
            .build()
        return exec(Request.Builder().url(url).build())
    }

    private inline fun <reified T> exec(request: Request): T {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("HTTP ${response.code}")
            val envelope = json.decodeFromString<ApiEnvelope<T>>(text)
            if (envelope.code != 200) throw ApiException("业务码 ${envelope.code}")
            return envelope.data ?: throw ApiException("响应数据为空")
        }
    }

    private fun decrypt(base64Text: String): String {
        val cipherText = Base64.decode(base64Text.trim(), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(KEY, "AES"),
            IvParameterSpec(KEY.copyOfRange(0, 16)),
        )
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    companion object {
        const val BASE = "https://api.haojiao.cc"
        /** 无畏契约分区 */
        const val GAME_ID = "t2Ud5pOQlscKLbRC"
        const val VERSION = "1.52.159"

        private const val SALT = "N61P#=Pf\$yz=fwFZa)U8"
        private val KEY = "B-:9bzB8K%~q{Au?^>Pfl*)k".toByteArray(Charsets.UTF_8)
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
