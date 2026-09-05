package com.rzh.valo.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TtlCacheTest {

    private var clock = 0L

    private fun cache(ttl: Long = 1000L) = TtlCache<String, Int>(ttl) { clock }

    @Test
    fun `TTL 内命中缓存不重复拉取`() = runTest {
        val cache = cache()
        var fetches = 0
        assertEquals(1, cache.get("k", force = false) { ++fetches })
        clock += 999
        assertEquals(1, cache.get("k", force = false) { ++fetches })
        assertEquals(1, fetches)
    }

    @Test
    fun `过期后重新拉取`() = runTest {
        val cache = cache()
        var fetches = 0
        cache.get("k", force = false) { ++fetches }
        clock += 1000
        assertEquals(2, cache.get("k", force = false) { ++fetches })
    }

    @Test
    fun `force 绕过缓存并刷新条目`() = runTest {
        val cache = cache()
        var fetches = 0
        cache.get("k", force = false) { ++fetches }
        assertEquals(2, cache.get("k", force = true) { ++fetches })
        // force 拉取后缓存已更新，后续命中新值
        assertEquals(2, cache.get("k", force = false) { ++fetches })
    }

    @Test
    fun `同 key 并发只拉取一次`() = runTest {
        val cache = cache()
        var fetches = 0
        val first = async { cache.get("k", force = false) { delay(100); ++fetches } }
        val second = async { cache.get("k", force = false) { delay(100); ++fetches } }
        assertEquals(1, first.await())
        assertEquals(1, second.await())
        assertEquals(1, fetches)
    }

    @Test
    fun `超过容量淘汰最早条目`() = runTest {
        val cache = cache(ttl = Long.MAX_VALUE)
        var fetches = 0
        repeat(TtlCache.MAX_ENTRIES + 1) { i ->
            clock += 1
            cache.get("k$i", force = false) { ++fetches }
        }
        val before = fetches
        // k0 最早写入，已被淘汰 → 重新拉取
        cache.get("k0", force = false) { ++fetches }
        assertEquals(before + 1, fetches)
        // 最新条目仍在缓存
        cache.get("k${TtlCache.MAX_ENTRIES}", force = false) { ++fetches }
        assertEquals(before + 1, fetches)
    }
}