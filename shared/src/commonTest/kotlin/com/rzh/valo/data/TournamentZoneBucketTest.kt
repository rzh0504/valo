package com.rzh.valo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TournamentZoneBucketTest {

    @Test
    fun `全球大区`() {
        assertEquals(TournamentZones.GLOBAL, tournamentZoneBucket("全球（Global）"))
    }

    @Test
    fun `中国大区含 CN 别名但不含港台`() {
        assertEquals(TournamentZones.CHINA, tournamentZoneBucket("中国 赛区"))
        assertEquals(TournamentZones.CHINA, tournamentZoneBucket("CN赛区"))
        // zone/list 官方树：港台属 Pacific
        assertEquals(TournamentZones.PACIFIC, tournamentZoneBucket("中国港台 赛区"))
    }

    @Test
    fun `太平洋大区`() {
        listOf("太平洋赛区", "日本 赛区", "韩国 赛区", "东南亚 赛区", "亚太 赛区", "南亚 赛区", "大洋洲 赛区", "马来西亚&新加坡 赛区")
            .forEach { assertEquals(it, TournamentZones.PACIFIC, tournamentZoneBucket(it)) }
    }

    @Test
    fun `EMEA 大区含 tree 外别名`() {
        listOf("EMEA赛区", "欧洲 赛区", "土耳其 赛区", "德语 赛区", "独联体 赛区", "中东北非 赛区", "黎凡特&北非 赛区", "海湾&伊拉克 赛区")
            .forEach { assertEquals(it, TournamentZones.EMEA, tournamentZoneBucket(it)) }
    }

    @Test
    fun `美洲大区含拉美别名`() {
        listOf("美洲赛区", "北美 赛区", "南美赛区", "巴西 赛区", "拉美北 赛区", "拉美南 赛区", "拉丁美洲 赛区")
            .forEach { assertEquals(it, TournamentZones.AMERICAS, tournamentZoneBucket(it)) }
    }

    @Test
    fun `空值与未知赛区返回 null`() {
        assertEquals(null, tournamentZoneBucket(null))
        assertEquals(null, tournamentZoneBucket("未知赛区"))
    }
}
