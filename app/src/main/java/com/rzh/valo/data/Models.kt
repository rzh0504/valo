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
    /** 可选，按赛事过滤（实测 2026-09-27：无时间窗时也可用，count 为该赛事全部场数） */
    @SerialName("tournament_id") val tournamentId: String? = null,
)

@Serializable
data class ApiEnvelope<T>(val code: Int = 0, val data: T? = null)

@Serializable
data class MatchListData(val count: Int = 0, val list: List<MatchItem> = emptyList())

@Serializable
data class BattleDetailData(val match: MatchItem? = null)

/** 战队队史类接口的 POST 请求体（get_team_record / get_valorant_stats） */
@Serializable
data class TeamStatsRequest(
    @SerialName("team_id") val teamId: String,
    @SerialName("game_id") val gameId: String,
)

/** team/reward 的分页包裹 */
@Serializable
data class TeamRewardData(val count: Int = 0, val list: List<TeamReward> = emptyList())

/** H2H / 近期战绩的胜负汇总（score_count 结构） */
@Serializable
data class TeamRecentSummary(
    @SerialName("match_total_count") val total: Int = 0,
    @SerialName("match_win_count") val win: Int = 0,
    @SerialName("match_win_rate") val winRate: Double = 0.0,
    @SerialName("match_lose_count") val lose: Int = 0,
)

/** 前瞻 · 历史交手记录（fight_big_match，两队 H2H 胜负统计与交手列表） */
@Serializable
data class FightHistoryData(
    @SerialName("main_score_count") val mainScoreCount: TeamRecentSummary? = null,
    @SerialName("guest_score_count") val guestScoreCount: TeamRecentSummary? = null,
    @SerialName("match_list") val matchList: List<MatchItem> = emptyList(),
)

/** 前瞻 · 单张地图上双方的历史战绩（valorant_map） */
@Serializable
data class MapRecord(
    @SerialName("map_id") val id: String = "",
    @SerialName("name_zh") val nameZh: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    val icon: String? = null,
    @SerialName("main_match_times") val mainMatches: Int = 0,
    @SerialName("main_win_times") val mainWins: Int = 0,
    @SerialName("guest_match_times") val guestMatches: Int = 0,
    @SerialName("guest_win_times") val guestWins: Int = 0,
) {
    val displayName: String get() = nameZh?.takeIf { it.isNotBlank() } ?: nameEn.orEmpty()
    val hasData: Boolean get() = mainMatches > 0 || guestMatches > 0
}

// ---------- 战队详情（team/base、team/roster、team/get_stats、team/reward 等） ----------

/** 战队基础信息（team/base）。participant_id 与 team_id 同一体系，可直接从比赛数据跳转 */
@Serializable
data class TeamBase(
    @SerialName("unique_id") val id: String = "",
    @SerialName("name_main") val nameMain: String? = null,
    @SerialName("name_sub") val nameSub: String? = null,
    @SerialName("name_short") val nameShort: String? = null,
    val icon: String? = null,
    @SerialName("zone_name") val zoneName: String? = null,
    @SerialName("zone_icon") val zoneIcon: String? = null,
    val describe: String? = null,
    val city: String? = null,
    @SerialName("establish_date_str") val establishDate: String? = null,
    val links: List<LinkInfo> = emptyList(),
    @SerialName("previous_team_info") val previousTeam: List<TeamRef> = emptyList(),
) {
    val displayName: String get() = nameMain.orEmpty()
}

/** 战队引用（前身战队、last_match 里的队伍等，team_id + 名称 + 队标） */
@Serializable
data class TeamRef(
    @SerialName("team_id") val id: String = "",
    @SerialName("team_name") val name: String? = null,
    @SerialName("team_name_short") val short: String? = null,
    @SerialName("team_icon") val icon: String? = null,
)

/** 名单成员（team/roster，选手或教练组通用的条目结构） */
@Serializable
data class RosterMember(
    @SerialName("join_date_str") val joinDate: String? = null,
    @SerialName("leave_date_str") val leaveDate: String? = null,
    @SerialName("nationality_icon") val nationalityIcon: String? = null,
    @SerialName("is_sub") val isSub: Boolean = false,
    @SerialName("is_inactive") val isInactive: Boolean = false,
    @SerialName("player_info") val person: RosterPerson? = null,
    @SerialName("career_info") val career: RosterCareer? = null,
)

@Serializable
data class RosterPerson(
    @SerialName("person_id") val id: String = "",
    @SerialName("person_name") val name: String? = null,
    @SerialName("person_icon") val icon: String? = null,
)

@Serializable
data class RosterCareer(
    @SerialName("career_id") val id: String = "",
    /** 1 选手 · 2 经理/教练组 */
    @SerialName("career_type") val type: Int = 0,
    val position: String? = null,
    val role: String? = null,
    @SerialName("career_id_name") val idName: String? = null,
) {
    /** 展示用职责：role（经理/主教练…）优先，其次位置（决斗/哨卫…） */
    val roleLabel: String get() = role?.takeIf { it.isNotBlank() } ?: position.orEmpty()
}

@Serializable
data class TeamRoster(
    @SerialName("active_rosters") val active: List<RosterMember> = emptyList(),
    @SerialName("former_rosters") val former: List<RosterMember> = emptyList(),
    @SerialName("organization_rosters") val organization: List<RosterMember> = emptyList(),
)

/** 战队近期数据（team/get_stats，full=false 约 2026 年内） */
@Serializable
data class TeamStats(
    @SerialName("match_count") val matchCount: Int = 0,
    @SerialName("match_win_count") val matchWinCount: Int = 0,
    @SerialName("game_count") val gameCount: Int = 0,
    @SerialName("game_win_count") val gameWinCount: Int = 0,
    @SerialName("map_stats") val mapStats: List<TeamMapStats> = emptyList(),
)

/** 单张地图的战队数据（squad/round_results 未使用，忽略） */
@Serializable
data class TeamMapStats(
    @SerialName("map_id") val id: String = "",
    @SerialName("map_zh_name") val nameZh: String? = null,
    @SerialName("map_en_name") val nameEn: String? = null,
    @SerialName("map_icon") val icon: String? = null,
    @SerialName("game_count") val games: Int = 0,
    @SerialName("game_win_count") val wins: Int = 0,
) {
    val displayName: String get() = nameZh?.takeIf { it.isNotBlank() } ?: nameEn.orEmpty()
}

/** 全队史总战绩（tournament/get_team_record） */
@Serializable
data class TeamRecord(
    @SerialName("match_count") val matchCount: Int = 0,
    @SerialName("match_win_count") val matchWinCount: Int = 0,
    @SerialName("round_count") val roundCount: Int = 0,
    @SerialName("round_win_count") val roundWinCount: Int = 0,
)

/** 选手数据条目（tournament/get_valorant_stats，队史所有选手） */
@Serializable
data class PlayerStatsRow(
    @SerialName("player_info") val person: PlayerStatsPerson? = null,
    @SerialName("career_info") val career: PlayerStatsCareer? = null,
    val heroes: List<HeroUsage> = emptyList(),
    @SerialName("round_num") val rounds: Int = 0,
    val acs: Double = 0.0,
    val kd: Double = 0.0,
    val kda: Double = 0.0,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val adr: Double = 0.0,
    val kast: Double = 0.0,
    val hs: Double = 0.0,
    val fk: Int = 0,
) {
    /** 1 现役 · 2 已离队 */
    val isActive: Boolean get() = career?.careerStatus == 1
}

@Serializable
data class PlayerStatsPerson(
    @SerialName("person_id") val id: String = "",
    @SerialName("real_name") val realName: String? = null,
    val icon: String? = null,
    @SerialName("nationality_icon") val nationalityIcon: String? = null,
)

@Serializable
data class PlayerStatsCareer(
    @SerialName("career_id") val id: String = "",
    @SerialName("id_name") val idName: String? = null,
    @SerialName("career_status") val careerStatus: Int = 0,
)

@Serializable
data class HeroUsage(
    @SerialName("hero_zh") val nameZh: String? = null,
    @SerialName("hero_en") val nameEn: String? = null,
    val icon: String? = null,
    val times: Int = 0,
) {
    val displayName: String get() = nameZh?.takeIf { it.isNotBlank() } ?: nameEn.orEmpty()
}

/** 赛事名次与奖金（team/reward，一条 = 一次赛事征程） */
@Serializable
data class TeamReward(
    val rank: String? = null,
    @SerialName("reward_date_str") val date: String? = null,
    @SerialName("tournament_group_info") val group: GroupInfo? = null,
    @SerialName("match_count") val matchCount: Int = 0,
    @SerialName("match_win_count") val matchWinCount: Int = 0,
    @SerialName("game_count") val gameCount: Int = 0,
    @SerialName("game_win_count") val gameWinCount: Int = 0,
    val bonus: String? = null,
)

/** 全地图列表（game/valorant_map），map_status：1 = 当前竞技图池内，2 = 已移出 */
@Serializable
data class GameMap(
    @SerialName("unique_id") val id: String = "",
    @SerialName("name_zh") val nameZh: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    val icon: String? = null,
    @SerialName("map_status") val status: Int = 0,
)

// ---------- 赛事（stage_with_group / integral_table） ----------

/** 赛事阶段（stage_id 供积分表、对阵图使用） */
@Serializable
data class TournamentStage(
    @SerialName("unique_id") val id: String = "",
    @SerialName("stage_name") val name: String? = null,
    @SerialName("start_date") val startDate: Long = 0L,
    @SerialName("end_date") val endDate: Long = 0L,
    @SerialName("group_list") val groupList: List<StageGroup> = emptyList(),
)

@Serializable
data class StageGroup(
    @SerialName("unique_id") val id: String = "",
    @SerialName("group_name") val name: String? = null,
)

/** 阶段积分榜的一个分组（Alpha/Omega 等） */
@Serializable
data class IntegralGroup(
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("integral_list") val rows: List<IntegralRow> = emptyList(),
)

@Serializable
data class IntegralRow(
    @SerialName("participant_info") val team: Participant? = null,
    val win: Int = 0,
    val lose: Int = 0,
    val draw: Int = 0,
    @SerialName("round_win") val roundWin: Int = 0,
    @SerialName("round_lose") val roundLose: Int = 0,
) {
    val roundDiff: Int get() = roundWin - roundLose
}

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
data class StageInfo(
    @SerialName("stage_id") val id: String? = null,
    @SerialName("stage_name") val name: String? = null,
)

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
