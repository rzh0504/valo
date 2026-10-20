package com.rzh.valo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterBySeriesTest {

    private fun match(id: String, tournamentId: String? = null) =
        MatchItem(id = id, tournament = tournamentId?.let { TournamentInfo(id = it) })

    private val seriesMap = mapOf("t-gc" to "s-gc", "t-vct" to "s-vct")

    @Test
    fun `屏蔽系列隐藏其赛事的比赛`() {
        val items = listOf(match("m1", "t-gc"), match("m2", "t-vct"))
        assertEquals(listOf("m2"), items.filterBySeries(seriesMap, setOf("s-gc")).map { it.id })
    }

    @Test
    fun `未收录或无系列的赛事始终保留`() {
        val items = listOf(match("m1"), match("m2", "t-unknown"), match("m3", "t-gc"))
        assertEquals(listOf("m1", "m2"), items.filterBySeries(seriesMap, setOf("s-gc")).map { it.id })
    }

    @Test
    fun `未屏蔽任何系列时不过滤`() {
        val items = listOf(match("m1", "t-gc"), match("m2"))
        assertEquals(items, items.filterBySeries(seriesMap, emptySet()))
    }

    @Test
    fun `多个系列同时屏蔽`() {
        val items = listOf(match("m1", "t-gc"), match("m2", "t-vct"), match("m3", "t-x"))
        assertEquals(listOf("m3"), items.filterBySeries(seriesMap, setOf("s-gc", "s-vct")).map { it.id })
    }
}
