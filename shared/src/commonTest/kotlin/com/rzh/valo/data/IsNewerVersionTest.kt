package com.rzh.valo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsNewerVersionTest {

    @Test
    fun `候选版本更新时返回 true`() {
        assertTrue(isNewerVersion("1.0.4", "1.0.5"))
        assertTrue(isNewerVersion("1.0.4", "1.1.0"))
        assertTrue(isNewerVersion("1.0.4", "2.0.0"))
    }

    @Test
    fun `相同或更旧返回 false`() {
        assertFalse(isNewerVersion("1.0.4", "1.0.4"))
        assertFalse(isNewerVersion("1.0.4", "1.0.3"))
        assertFalse(isNewerVersion("1.0.4", "0.9.9"))
    }

    @Test
    fun `容忍 v 前缀`() {
        assertTrue(isNewerVersion("1.0.4", "v1.0.5"))
        assertFalse(isNewerVersion("1.0.4", "v1.0.4"))
    }

    @Test
    fun `位数不足按 0 补齐`() {
        assertTrue(isNewerVersion("1.0", "1.0.1"))
        assertFalse(isNewerVersion("1.0.0", "1"))
        assertTrue(isNewerVersion("1", "2"))
    }
}
