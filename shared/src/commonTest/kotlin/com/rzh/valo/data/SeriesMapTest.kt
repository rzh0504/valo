package com.rzh.valo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesMapTest {

    private fun tournament(id: String, primary: TournamentSeriesInfo?, second: TournamentSeriesInfo? = null) =
        TournamentItem(id = id, series = primary, secondSeries = second)

    private fun series(id: String, name: String) = TournamentSeriesInfo(id = id, name = name)

    @Test
    fun `主系列优先 缺失时用子系列`() {
        val map = buildSeriesMap(
            listOf(
                tournament("t1", series("s-vct", "冠军巡回赛")),
                tournament("t2", null, series("s-gc", "改变者赛")),
            )
        )
        assertEquals("s-vct", map["t1"])
        assertEquals("s-gc", map["t2"])
        assertEquals(2, map.size)
    }

    @Test
    fun `无系列与空 id 的赛事不进映射`() {
        val map = buildSeriesMap(
            listOf(
                tournament("t1", null),
                tournament("t2", series("", "空 id")),
            )
        )
        assertEquals(emptyMap<String, String>(), map)
    }

    @Test
    fun `默认屏蔽目录中的非热门系列`() {
        assertTrue("w6ioCQaTSQTcZ5nm" in DEFAULT_HIDDEN_SERIES)
        assertEquals(
            setOf(
                "ZVDYkTwFSFrcOSDY",
                "oErcu1pVNzClW1O9",
                "hqWyEQWHbbsQqO7o",
                "fWumMCSnsdjbVB9M",
                "aNntuKCoRfQIxD3S",
                "pTDankGfC4YTtgS3",
            ),
            SERIES_CATALOG.filter { it.popular }.map { it.id }.toSet(),
        )
    }

    @Test
    fun `目录 id 不重复`() {
        assertEquals(SERIES_CATALOG.size, SERIES_CATALOG.map { it.id }.toSet().size)
    }

    @Test
    fun `标签拼接子系列缩写`() {
        assertEquals("冠军巡回赛 VCT", SeriesSummary("s-vct", "冠军巡回赛", "VCT").label)
        assertEquals("冠军巡回赛", SeriesSummary("s-vct", "冠军巡回赛", null).label)
        assertEquals("冠军巡回赛", SeriesSummary("s-vct", "冠军巡回赛", "  ").label)
    }
}
