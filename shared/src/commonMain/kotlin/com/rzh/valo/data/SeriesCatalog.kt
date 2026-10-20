package com.rzh.valo.data

/**
 * 系列赛目录：写死并随版本更新维护（2026-10 按全量赛事列表聚合实测），
 * 新增系列等下个版本收录，目录外系列的赛事始终显示。
 * [popular] 为默认显示的热门系列，其余默认屏蔽。
 */
data class SeriesSummary(
    val id: String,
    val name: String,
    val sub: String? = null,
    val popular: Boolean = false,
) {
    val label: String get() = if (sub.isNullOrBlank()) name else "$name $sub"
}

val SERIES_CATALOG = listOf(
    SeriesSummary("ZVDYkTwFSFrcOSDY", "冠军巡回赛", "VCT", popular = true),
    SeriesSummary("oErcu1pVNzClW1O9", "挑战者联赛", "VCL", popular = true),
    SeriesSummary("hqWyEQWHbbsQqO7o", "晋升赛", "Ascension", popular = true),
    SeriesSummary("fWumMCSnsdjbVB9M", "进化者系列公开赛", "CN ES", popular = true),
    SeriesSummary("aNntuKCoRfQIxD3S", "电竞世界杯", "ENC", popular = true),
    SeriesSummary("pTDankGfC4YTtgS3", "电竞俱乐部世界杯", "EWC", popular = true),
    SeriesSummary("w6ioCQaTSQTcZ5nm", "改变者赛", "GC'"),
    SeriesSummary("YMJDYFfmnuBrD5BV", "掠夺者电竞大赛", "Predator League"),
    SeriesSummary("MEBPSkvwncvPEudn", "休赛期邀请赛", "OFF//SEASON"),
    SeriesSummary("ffWtRFhKcw9WVpxH", "全国大赛", "VCNT"),
    SeriesSummary("6tqPOdxt8vNxStcF", "\"红牛主场\"公开赛", "RBHG"),
    SeriesSummary("JvGn8i5zRtzygs3D", "无畏契约官方授权赛事", "VCT Partner Series"),
    SeriesSummary("2RorF9cOxy7nqCZy", "聚光灯系列赛", "Spotlight"),
    SeriesSummary("v6bks8jHvzmdkaID", "FGC系列邀请赛", "FGC"),
    SeriesSummary("SslSdM85YCHYJ4tP", "电子竞技亚洲冠军联赛", "ACL"),
    SeriesSummary("EeyriLE7dKmJjmV7", "SVL锦标赛", "SVL"),
    SeriesSummary("7dcCWttp41Tg8tMZ", "解说主持杯", "VCC"),
    SeriesSummary("ZIcTLfySVa6FQL2b", "挑战者联赛 晋升赛资格系列赛", "VCL AQS"),
    SeriesSummary("P2ZRcptdwWVm5r1C", "中国-东盟电子体育大赛", "CAEC"),
    SeriesSummary("cWBhrhlVogvLC4Uz", "Fortress Grassroots Esports", "FGE"),
    SeriesSummary("PSRuj3kEOoh6LTLq", "Riot Games ONE", "Riot ONE"),
    SeriesSummary("culJerpAF59wc3zO", "TEN邀请赛", "TEN"),
    SeriesSummary("4axVz4KM3ycZPixD", "TPL系列赛事", "TPL"),
)

/** 默认屏蔽 = 目录中的非热门系列 */
val DEFAULT_HIDDEN_SERIES: Set<String> =
    SERIES_CATALOG.filterNot { it.popular }.map { it.id }.toSet()
