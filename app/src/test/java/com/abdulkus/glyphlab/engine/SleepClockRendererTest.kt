package com.abdulkus.glyphlab.engine

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepClockRendererTest {
    private val testTimeMillis: Long = LocalDateTime.of(2026, 8, 9, 12, 34)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun clockUsesFixedStockLikeTwoRowLayout() {
        val frame = SleepClockRenderer.render(testTimeMillis)

        assertEquals(MatrixEngine.PIXEL_COUNT, frame.size)
        assertTrue(frame.all { it == 0 || it == 255 })
        assertTrue(frame.any { it == 255 })

        val lit = frame.indices.filter { frame[it] > 0 }
        assertTrue(lit.all { index -> index % MatrixEngine.SIZE in 3..9 })
        assertTrue(lit.all { index -> index / MatrixEngine.SIZE in 1..11 })
        assertTrue(lit.none { index -> index / MatrixEngine.SIZE == 6 })
        assertTrue(lit.all { index ->
            MatrixEngine.isInsideMatrix(index % MatrixEngine.SIZE, index / MatrixEngine.SIZE)
        })
    }

    @Test
    fun rendersHoursAboveMinutesWithoutAnimationBackground() {
        val frame = SleepClockRenderer.render(testTimeMillis)

        fun row(y: Int): String = (0 until MatrixEngine.SIZE).joinToString("") { x ->
            if (frame[y * MatrixEngine.SIZE + x] > 0) "#" else "."
        }

        // 12 on top and 34 below, using the same fixed 3x5 alphabet as stock.
        assertEquals("....#..###...", row(1))
        assertEquals("...##....#...", row(2))
        assertEquals("....#..###...", row(3))
        assertEquals("....#..#.....", row(4))
        assertEquals("...###.###...", row(5))
        assertEquals(".............", row(6))
        assertEquals("...###.#.#...", row(7))
        assertEquals(".....#.#.#...", row(8))
        assertEquals("...###.###...", row(9))
        assertEquals(".....#...#...", row(10))
        assertEquals("...###...#...", row(11))
    }

    @Test
    fun clockEffectReusesPixelsWithinAMinuteAndRefreshesOnTheNextMinute() {
        val cache = MinuteClockFrameCache()
        val first = cache.frame(testTimeMillis, masterBrightness = 0.8f)
        val sameMinute = cache.frame(testTimeMillis + 20_000L, masterBrightness = 0.8f)
        val nextMinute = cache.frame(testTimeMillis + 60_000L, masterBrightness = 0.8f)

        assertSame(first, sameMinute)
        assertNotSame(first, nextMinute)
        assertTrue(first.all { it == 0 || it == 204 })
    }
}
