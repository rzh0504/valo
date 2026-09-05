package com.rzh.valo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeStartLabelTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `已到点或已开赛返回 null`() {
        assertNull(relativeStartLabel(now, now))
        assertNull(relativeStartLabel(now - 1, now))
    }

    @Test
    fun `一分钟内即将开始`() {
        assertEquals("即将开始", relativeStartLabel(now + 59_000, now))
    }

    @Test
    fun `一小时内按分钟`() {
        assertEquals("1 分钟后", relativeStartLabel(now + 60_000, now))
        assertEquals("59 分钟后", relativeStartLabel(now + 59 * 60_000, now))
    }

    @Test
    fun `超过一小时按小时取整`() {
        assertEquals("1 小时后", relativeStartLabel(now + 60 * 60_000, now))
        assertEquals("1 小时后", relativeStartLabel(now + 119 * 60_000, now))
        assertEquals("23 小时后", relativeStartLabel(now + 23 * 60 * 60_000, now))
    }
}