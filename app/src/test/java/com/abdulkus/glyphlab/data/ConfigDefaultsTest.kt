package com.abdulkus.glyphlab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigDefaultsTest {
    @Test
    fun minimumBrightnessDefaultsToOnePercent() {
        assertEquals(0.01f, MatrixConfig().minimumBrightness, 0.0001f)
    }
}
