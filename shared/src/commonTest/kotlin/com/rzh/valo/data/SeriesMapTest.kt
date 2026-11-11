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
        assertTrue("vb9uUS7HHCkedyCW" in DEFAULT_HIDDEN_SERIES)
        assertTrue("MDe1QzFOucnKexmp" in DEFAULT_HIDDEN_SERIES)
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
    fun `旧批次屏蔽集读取时补入后收录系列`() {
        val stored = setOf("w6ioCQaTSQTcZ5nm")
        val migrated = effectiveHiddenSeries(stored, revision = 0)
        assertTrue("MDe1QzFOucnKexmp" in migrated)
        assertTrue("vb9uUS7HHCkedyCW" in migrated)
        assertTrue("w6ioCQaTSQTcZ5nm" in migrated)
    }

    @Test
    fun `当前批次屏蔽集不再补入 保留用户取消的系列`() {
        // 用户在批次 1 之后手动放开了后补系列，读取时不应被重新屏蔽
        val stored = setOf("w6ioCQaTSQTcZ5nm")
        assertEquals(stored, effectiveHiddenSeries(stored, revision = 1))
    }

    @Test
    fun `从未编辑过过滤时用默认屏蔽集`() {
        assertEquals(DEFAULT_HIDDEN_SERIES, effectiveHiddenSeries(stored = null, revision = 0))
    }

    @Test
    fun `后补系列 id 均在目录中`() {
        val catalogIds = SERIES_CATALOG.map { it.id }.toSet()
        assertTrue(SERIES_ADDED_BY_REVISION.values.flatten().all { it in catalogIds })
    }

    @Test
    fun `目录 id 不重复`() {
        assertEquals(SERIES_CATALOG.size, SERIES_CATALOG.map { it.id }.toSet().size)
    }

    @Test
    fun `目录条目均带徽标`() {
        assertTrue(SERIES_CATALOG.all { !it.icon.isNullOrBlank() })
    }

    @Test
    fun `标签拼接子系列缩写`() {
        assertEquals("冠军巡回赛 VCT", SeriesSummary("s-vct", "冠军巡回赛", "VCT").label)
        assertEquals("冠军巡回赛", SeriesSummary("s-vct", "冠军巡回赛", null).label)
        assertEquals("冠军巡回赛", SeriesSummary("s-vct", "冠军巡回赛", "  ").label)
    }
}
