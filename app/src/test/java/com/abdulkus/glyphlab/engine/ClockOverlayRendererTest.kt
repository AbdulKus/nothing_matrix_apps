package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.MatrixConfig
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockOverlayRendererTest {
    private val testTimeMillis: Long = LocalDateTime.of(2026, 8, 9, 12, 34)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun disabledClockLeavesFrameUntouched() {
        val frame = IntArray(MatrixEngine.PIXEL_COUNT) { it % 256 }
        val result = ClockOverlayRenderer.apply(frame, MatrixConfig(clockEnabled = false), testTimeMillis)

        assertTrue(frame.contentEquals(result))
    }

    @Test
    fun oneLineClockFitsInsidePhysicalMatrix() {
        val result = ClockOverlayRenderer.apply(
            IntArray(MatrixEngine.PIXEL_COUNT),
            MatrixConfig(
                clockEnabled = true,
                clockTwoLines = false,
                clockScale = 1f,
                brightness = 1f
            ),
            testTimeMillis
        )

        assertTrue(result.count { it > 0 } >= 12)
        result.indices.filter { result[it] > 0 }.forEach { index ->
            assertTrue(MatrixEngine.isInsideMatrix(index % MatrixEngine.SIZE, index / MatrixEngine.SIZE))
        }
    }

    @Test
    fun twoLineClockUsesMoreVerticalSpaceThanOneLine() {
        fun verticalSpan(twoLines: Boolean): Int {
            val result = ClockOverlayRenderer.apply(
                IntArray(MatrixEngine.PIXEL_COUNT),
                MatrixConfig(
                    clockEnabled = true,
                    clockTwoLines = twoLines,
                    clockScale = 0.7f,
                    brightness = 1f
                ),
                testTimeMillis
            )
            val ys = result.indices.filter { result[it] > 0 }.map { it / MatrixEngine.SIZE }
            return ys.max() - ys.min()
        }

        assertTrue(verticalSpan(true) > verticalSpan(false))
    }

    @Test
    fun outlineDarkensOnlyAroundClock() {
        val frame = IntArray(MatrixEngine.PIXEL_COUNT) { index ->
            if (MatrixEngine.isInsideMatrix(index % MatrixEngine.SIZE, index / MatrixEngine.SIZE)) 100 else 0
        }
        val result = ClockOverlayRenderer.apply(
            frame,
            MatrixConfig(
                clockEnabled = true,
                clockOutline = 1f,
                brightness = 1f
            ),
            testTimeMillis
        )

        assertTrue(result.any { it == 0 })
        assertTrue(result.any { it == 255 })
        assertFalse(frame.contentEquals(result))
    }

    @Test
    fun invertUsesOppositeClockLuminanceInsteadOfWhite() {
        val frame = IntArray(MatrixEngine.PIXEL_COUNT) { index ->
            if (MatrixEngine.isInsideMatrix(index % MatrixEngine.SIZE, index / MatrixEngine.SIZE)) 50 else 0
        }
        val result = ClockOverlayRenderer.apply(
            frame,
            MatrixConfig(
                clockEnabled = true,
                clockInvert = true,
                brightness = 1f
            ),
            testTimeMillis
        )

        assertEquals(205, result.max())
        assertTrue(result.any { it == 50 })
    }

    @Test
    fun zeroMasterBrightnessKeepsClockOff() {
        val result = ClockOverlayRenderer.apply(
            IntArray(MatrixEngine.PIXEL_COUNT),
            MatrixConfig(clockEnabled = true, brightness = 0f),
            testTimeMillis
        )

        assertTrue(result.all { it == 0 })
    }
}
