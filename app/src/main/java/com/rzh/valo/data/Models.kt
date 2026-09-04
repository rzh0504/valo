package com.rzh.valo.data

import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 站点赛程时间以中国标准时间为准，展示统一使用 Asia/Shanghai，不跟随设备时区 */
val CN_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** 比赛状态（实测语义）：1 未开始 · 2 进行中 · 3 已结束 */
object MatchStatus {
    const val SCHEDULED = 1
    const val LIVE = 2
    const val FINISHED = 3
}

@Serializable
data class MatchListRequest(
    @SerialName("game_id") val gameId: String,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val platform: String,
    @SerialName("match_status") val matchStatus: List<Int>,
    @SerialName("sort_by_start_time") val sortByStartTime: Int,
    @SerialName("start_time") val startTime: Long? = null,
    @SerialName("end_time") val endTime: Long? = null,
)

@Serializable
data class ApiEnvelope<T>(val code: Int = 0, val data: T? = null)

@Serializable
data class MatchListData(val count: Int = 0, val list: List<MatchItem> = emptyList())

@Serializable
data class BattleDetailData(val match: MatchItem? = null)

/** 比赛（列表项与详情共用，unknown 字段一律忽略） */
@Serializable
data class MatchItem(
    @SerialName("unique_id") val id: String = "",
    @SerialName("tournament_info") val tournament: TournamentInfo? = null,
    @SerialName("tournament_group_info") val group: GroupInfo? = null,
    @SerialName("stage_info") val stage: StageInfo? = null,
    @SerialName("schedule_name") val scheduleName: String? = null,
    /** BO 局数：3 → BO3 */
    @SerialName("match_num_type") val boNum: Int = 0,
    @SerialName("match_start_time") val startTime: Long = 0L,
    @SerialName("match_status") val status: Int = 0,
    @SerialName("versus_info") val versus: VersusInfo? = null,
    val links: List<LinkInfo> = emptyList(),
    val level: String? = null,
) {
    val isLive: Boolean get() = status == MatchStatus.LIVE
    val isFinished: Boolean get() = status == MatchStatus.FINISHED
}

@Serializable
data class TournamentInfo(
    @SerialName("tournament_id") val id: String? = null,
    @SerialName("name_main") val nameMain: String? = null,
    @SerialName("name_sub") val nameSub: String? = null,
    @SerialName("supple_text") val suppleText: String? = null,
    val icon: String? = null,
)

@Serializable
data class GroupInfo(
    @SerialName("tournament_group_id") val id: String? = null,
    @SerialName("name_main") val nameMain: String? = null,
    @SerialName("name_sub") val nameSub: String? = null,
    val icon: String? = null,
)

@Serializable
data class StageInfo(@SerialName("stage_name") val name: String? = null)

/** 对阵信息；未开赛时两侧 camp 可能为 null（队伍待定） */
@Serializable
data class VersusInfo(
    @SerialName("main_camp") val mainCamp: List<Participant>? = null,
    @SerialName("main_score") val mainScore: String? = null,
    @SerialName("guest_camp") val guestCamp: List<Participant>? = null,
    @SerialName("guest_score") val guestScore: String? = null,
    /** 1 主队胜 · 2 客队胜 · 3 未决出 */
    @SerialName("is_main_win") val isMainWin: Int = 0,
)

@Serializable
data class Participant(
    @SerialName("participant_id") val id: String? = null,
    @SerialName("name_main") val nameMain: String? = null,
    @SerialName("name_short") val nameShort: String? = null,
    val icon: String? = null,
    val tag: String? = null,
    @SerialName("tag_icon") val tagIcon: String? = null,
    val color: String? = null,
    val desc: String? = null,
) {
    val displayName: String get() = nameShort?.takeIf { it.isNotBlank() } ?: nameMain.orEmpty()
}

/** link_type：1 直播/转播 · 3 官网/社交等 */
@Serializable
data class LinkInfo(
    @SerialName("link_type") val type: Int = 0,
    val platform: Int = 0,
    val url: String? = null,
    val desc: String? = null,
)

// ---------- 比赛明细（地图 / 小局 / 选手，get_valorant_round） ----------

@Serializable
data class RoundData(
    /** 全场选手汇总 */
    val all: List<PlayerMatchStats> = emptyList(),
    /** 每张地图一个小局 */
    val list: List<MapRoundData> = emptyList(),
)

@Serializable
data class MapRoundData(
    val map: MapInfo? = null,
    @SerialName("main_score") val mainScore: ScoreHalf? = null,
    @SerialName("guest_score") val guestScore: ScoreHalf? = null,
    @SerialName("main_team") val mainTeam: RoundTeam? = null,
    @SerialName("guest_team") val guestTeam: RoundTeam? = null,
    @SerialName("round_detail") val rounds: List<RoundEntry> = emptyList(),
    @SerialName("is_overtime") val isOvertime: Boolean = false,
    @SerialName("is_end") val isEnd: Boolean = false,
    @SerialName("player_info") val players: List<PlayerMapStats> = emptyList(),
)

@Serializable
data class MapInfo(
    @SerialName("map_id") val id: String? = null,
    @SerialName("name_zh") val nameZh: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    val icon: String? = null,
) {
    val displayName: String get() = nameZh?.takeIf { it.isNotBlank() } ?: nameEn.orEmpty()
}

/** 一局的比分：总比分 + 进攻/防守半场 + 加时 */
@Serializable
data class ScoreHalf(
    val total: Int = 0,
    val atk: Int = 0,
    val def: Int = 0,
    @SerialName("over_time_atk") val otAtk: Int = 0,
    @SerialName("over_time_def") val otDef: Int = 0,
)

@Serializable
data class RoundTeam(
    @SerialName("team_id") val id: String? = null,
    @SerialName("team_name") val name: String? = null,
    @SerialName("team_name_short") val short: String? = null,
    @SerialName("team_icon") val icon: String? = null,
)

/**
 * 单回合结果（实测语义）：win_team 1=主队 2=客队；
 * win_camp 获胜方阵营 1=防守 2=进攻；
 * mode 获胜方式 1=时间耗尽 2=引爆 3=歼灭 4=拆除
 */
@Serializable
data class RoundEntry(
    @SerialName("win_team") val winTeam: Int = 0,
    @SerialName("win_camp") val winCamp: Int = 0,
    val mode: Int = 0,
)

@Serializable
data class PlayerProfile(
    @SerialName("person_id") val id: String? = null,
    @SerialName("real_name") val realName: String? = null,
    @SerialName("real_name_en") val realNameEn: String? = null,
    val icon: String? = null,
    @SerialName("nationality_icon") val nationalityIcon: String? = null,
)

@Serializable
data class CareerInfo(
    @SerialName("id_name") val idName: String? = null,
    val position: String? = null,
)

@Serializable
data class HeroInfo(
    @SerialName("hero_zh") val nameZh: String? = null,
    @SerialName("hero_en") val nameEn: String? = null,
    val icon: String? = null,
)

/** 单图选手数据 */
@Serializable
data class PlayerMapStats(
    @SerialName("is_main") val isMain: Boolean = false,
    @SerialName("player_info") val player: PlayerProfile? = null,
    @SerialName("career_info") val career: CareerInfo? = null,
    val hero: HeroInfo? = null,
    val acs: Double? = null,
    val rating: Double? = null,
    val kast: Double? = null,
    val adr: Double? = null,
    val hs: Double? = null,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val sort: Int = 0,
)

/** 全场选手汇总（all[]，hero 为使用过的英雄列表） */
@Serializable
data class PlayerMatchStats(
    @SerialName("is_main") val isMain: Boolean = false,
    @SerialName("player_info") val player: PlayerProfile? = null,
    @SerialName("career_info") val career: CareerInfo? = null,
    val hero: List<HeroInfo> = emptyList(),
    @SerialName("round_times") val rounds: Int = 0,
    val acs: Double? = null,
    val rating: Double? = null,
    val kast: Double? = null,
    val adr: Double? = null,
    val hs: Double? = null,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
)
