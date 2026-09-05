package com.rzh.valo.ui.detail

import com.rzh.valo.data.MapRoundData
import com.rzh.valo.data.RoundEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class GridCellCountTest {

    private fun map(roundCount: Int, isEnd: Boolean) = MapRoundData(
        rounds = List(roundCount) { RoundEntry() },
        isEnd = isEnd,
    )

    @Test
    fun `完赛按实际回合数`() {
        assertEquals(21, gridCellCount(map(21, isEnd = true)))
        assertEquals(13, gridCellCount(map(13, isEnd = true)))
    }

    @Test
    fun `未完赛补齐到整组 12`() {
        assertEquals(12, gridCellCount(map(5, isEnd = false)))
        assertEquals(12, gridCellCount(map(12, isEnd = false)))
        assertEquals(24, gridCellCount(map(13, isEnd = false)))
    }

    @Test
    fun `无回合返回 0`() {
        assertEquals(0, gridCellCount(map(0, isEnd = false)))
        assertEquals(0, gridCellCount(map(0, isEnd = true)))
    }
}