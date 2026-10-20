package com.rzh.valo.data

/**
 * 系列赛目录：写死并随版本更新维护（2026-10 按全量赛事列表聚合实测），
 * 新增系列等下个版本收录，目录外系列的赛事始终显示。
 * [popular] 为默认显示的热门系列，其余默认屏蔽；[icon] 为徽标资源路径（files 域名下）。
 */
data class SeriesSummary(
    val id: String,
    val name: String,
    val sub: String? = null,
    val icon: String? = null,
    val popular: Boolean = false,
) {
    val label: String get() = if (sub.isNullOrBlank()) name else "$name $sub"
}

val SERIES_CATALOG = listOf(
    SeriesSummary("ZVDYkTwFSFrcOSDY", "冠军巡回赛", "VCT", "/cms/20240912/1726130881087.png", popular = true),
    SeriesSummary("oErcu1pVNzClW1O9", "挑战者联赛", "VCL", "/cms/20240806/1722911087302.png", popular = true),
    SeriesSummary("hqWyEQWHbbsQqO7o", "晋升赛", "Ascension", "/cms/20230607/1686126551476.jpg", popular = true),
    SeriesSummary("fWumMCSnsdjbVB9M", "进化者系列公开赛", "CN ES", "/cms/20230827/1693094461403.png", popular = true),
    SeriesSummary("aNntuKCoRfQIxD3S", "电竞世界杯", "ENC", "/cms/20260225/1771988569487.png", popular = true),
    SeriesSummary("pTDankGfC4YTtgS3", "电竞俱乐部世界杯", "EWC", "/cms/20260514/1778727822419.png", popular = true),
    SeriesSummary("w6ioCQaTSQTcZ5nm", "改变者赛", "GC'", "/cms/20230613/1686623871103.jpg"),
    SeriesSummary("YMJDYFfmnuBrD5BV", "掠夺者电竞大赛", "Predator League", "/cms/20241203/1733191384724.png"),
    SeriesSummary("MEBPSkvwncvPEudn", "休赛期邀请赛", "OFF//SEASON", "/cms/20231007/1696652338594.png"),
    SeriesSummary("ffWtRFhKcw9WVpxH", "全国大赛", "VCNT", "/cms/20260715/1784123022036.png"),
    SeriesSummary("6tqPOdxt8vNxStcF", "\"红牛主场\"公开赛", "RBHG", "/cms/20251015/1760496524939.jpg"),
    SeriesSummary("JvGn8i5zRtzygs3D", "无畏契约官方授权赛事", "VCT Partner Series", "/cms/20250908/1757310919246.png"),
    SeriesSummary("2RorF9cOxy7nqCZy", "聚光灯系列赛", "Spotlight", "/cms/20251025/1761403793659.png"),
    SeriesSummary("v6bks8jHvzmdkaID", "FGC系列邀请赛", "FGC", "/cms/20230628/1687920368813.png"),
    SeriesSummary("SslSdM85YCHYJ4tP", "电子竞技亚洲冠军联赛", "ACL", "/cms/20250331/1743399608938.png"),
    SeriesSummary("EeyriLE7dKmJjmV7", "SVL锦标赛", "SVL", "/cms/20251022/1761104829299.png"),
    SeriesSummary("7dcCWttp41Tg8tMZ", "解说主持杯", "VCC", "/cms/20241126/1732609139716.png"),
    SeriesSummary("ZIcTLfySVa6FQL2b", "挑战者联赛 晋升赛资格系列赛", "VCL AQS", "/cms/20240716/1721100138443.jpg"),
    SeriesSummary("P2ZRcptdwWVm5r1C", "中国-东盟电子体育大赛", "CAEC", "/cms/20250922/1758510143193.png"),
    SeriesSummary("cWBhrhlVogvLC4Uz", "Fortress Grassroots Esports", "FGE", "/cms/20260622/1782119203820.png"),
    SeriesSummary("PSRuj3kEOoh6LTLq", "Riot Games ONE", "Riot ONE", "/cms/20241125/1732516111646.png"),
    SeriesSummary("culJerpAF59wc3zO", "TEN邀请赛", "TEN", "/cms/20251022/1761102706738.png"),
    SeriesSummary("4axVz4KM3ycZPixD", "TPL系列赛事", "TPL", "/cms/20261016/1792114755500-tpl-logo-source-highres.png"),
)

/** 默认屏蔽 = 目录中的非热门系列 */
val DEFAULT_HIDDEN_SERIES: Set<String> =
    SERIES_CATALOG.filterNot { it.popular }.map { it.id }.toSet()
