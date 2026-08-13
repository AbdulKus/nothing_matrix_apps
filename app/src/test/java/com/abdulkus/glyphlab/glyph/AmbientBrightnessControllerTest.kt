package com.abdulkus.glyphlab.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientBrightnessControllerTest {
    @Test
    fun luxCurveIsMonotonicAndKeepsSafeNightFloor() {
        val lux = listOf(0f, 1f, 5f, 20f, 100f, 500f, 2_000f, 10_000f, 50_000f)
        val scales = lux.map { AmbientBrightnessController.targetScaleForLux(it) }

        assertEquals(0.07f, scales.first(), 0.0001f)
        assertEquals(1f, scales.last(), 0.0001f)
        assertTrue(scales.zipWithNext().all { (left, right) -> left <= right })
    }

    @Test
    fun interpolationUsesLogLuxInsteadOfHarshSteps() {
        val atFive = AmbientBrightnessController.targetScaleForLux(5f)
        val between = AmbientBrightnessController.targetScaleForLux(10f)
        val atTwenty = AmbientBrightnessController.targetScaleForLux(20f)

        assertTrue(between > atFive)
        assertTrue(between < atTwenty)
    }

    @Test
    fun subsequentReadingsMoveSmoothlyTowardTarget() {
        val controller = AmbientBrightnessController()
        controller.updateLux(100f, 1_000_000_000L)
        val start = controller.scale
        val afterOneSecond = controller.updateLux(10_000f, 2_000_000_000L)

        assertEquals(0.38f, start, 0.0001f)
        assertTrue(afterOneSecond > start)
        assertTrue(afterOneSecond < 1f)
    }

    @Test
    fun invalidSensorReadingDoesNotChangeBrightness() {
        val controller = AmbientBrightnessController(100f)
        val before = controller.scale

        assertEquals(before, controller.updateLux(Float.NaN, 1_000_000_000L), 0f)
        assertEquals(before, controller.updateLux(-1f, 2_000_000_000L), 0f)
    }
}
