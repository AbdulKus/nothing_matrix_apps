package com.abdulkus.glyphlab.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareFrameMapperTest {
    @Test
    fun glyphToyOutputNeverExceeds12BitRange() {
        val mapped = HardwareFrameMapper.forGlyphToy(
            frame = intArrayOf(255, 128, 1, 0),
            masterBrightness = 1f,
            automaticScale = 1f,
            minimumBrightness = 0f
        )

        assertEquals(4095, mapped[0])
        assertTrue(mapped.all { it in 0..4095 })
    }

    @Test
    fun onePercentMinimumRemainsLowAtZeroAutoScale() {
        val mapped = HardwareFrameMapper.forGlyphToy(
            frame = intArrayOf(255),
            masterBrightness = 0.9f,
            automaticScale = 0f,
            minimumBrightness = 0.01f
        )

        assertTrue(mapped[0] in 1..250)
    }
}
