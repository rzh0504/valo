package com.rzh.valo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterByLevelsTest {

    private fun match(level: String?) = MatchItem(id = "id-$level", level = level)

    @Test
    fun `仅保留勾选的级别`() {
        val items = listOf(match("S"), match("A"), match("B"), match("C"))
        assertEquals(listOf("S", "A"), items.filterByLevels(setOf("S", "A")).map { it.level })
    }

    @Test
    fun `未标注或未知级别始终保留`() {
        val items = listOf(match(null), match("X"), match("A"))
        assertEquals(listOf(null, "X"), items.filterByLevels(emptySet()).map { it.level })
    }

    @Test
    fun `级别匹配忽略大小写`() {
        val items = listOf(match("s"), match("a"))
        assertEquals(listOf("s"), items.filterByLevels(setOf("S")).map { it.level })
    }

    @Test
    fun `全部勾选时不过滤`() {
        val items = MATCH_LEVELS.map { match(it) } + match(null)
        assertEquals(items, items.filterByLevels(MATCH_LEVELS.toSet()))
    }
}