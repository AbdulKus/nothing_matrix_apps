package com.abdulkus.glyphlab.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBrightnessControllerTest {
    @Test
    fun luxCurveIsMonotonicAndCoversTheWholeConfiguredRange() {
        val lux = listOf(0f, 1f, 5f, 20f, 100f, 500f, 2_000f, 10_000f, 50_000f)
        val scales = lux.map { AutomaticBrightnessController.targetScaleForLux(it) }

        assertEquals(0f, scales.first(), 0.0001f)
        assertEquals(1f, scales.last(), 0.0001f)
        assertTrue(scales.zipWithNext().all { (left, right) -> left <= right })
    }

    @Test
    fun interpolationUsesLogLuxInsteadOfHarshSteps() {
        val atFive = AutomaticBrightnessController.targetScaleForLux(5f)
        val between = AutomaticBrightnessController.targetScaleForLux(10f)
        val atTwenty = AutomaticBrightnessController.targetScaleForLux(20f)

        assertTrue(between > atFive)
        assertTrue(between < atTwenty)
    }

    @Test
    fun screenBrightnessIsLinkedLinearlyAcrossTheConfiguredRange() {
        assertEquals(0f, AutomaticBrightnessController.targetScaleForScreenBrightness(0), 0.0001f)
        assertEquals(10f / 255f, AutomaticBrightnessController.targetScaleForScreenBrightness(10), 0.0001f)
        assertEquals(128f / 255f, AutomaticBrightnessController.targetScaleForScreenBrightness(128), 0.0001f)
        assertEquals(1f, AutomaticBrightnessController.targetScaleForScreenBrightness(255), 0.0001f)
    }

    @Test
    fun subsequentReadingsMoveSmoothlyTowardTarget() {
        val controller = AutomaticBrightnessController()
        controller.updateAmbientLux(100f, 1_000_000_000L)
        val start = controller.scale
        val afterOneSecond = controller.updateAmbientLux(10_000f, 2_000_000_000L)

        assertEquals(0.33f, start, 0.0001f)
        assertTrue(afterOneSecond > start)
        assertTrue(afterOneSecond < 1f)
    }

    @Test
    fun screenChangesUseTheSameSmoothing() {
        val controller = AutomaticBrightnessController()
        controller.updateScreenBrightness(64, 1_000_000_000L)
        val start = controller.scale
        val afterOneSecond = controller.updateScreenBrightness(255, 2_000_000_000L)

        assertEquals(64f / 255f, start, 0.0001f)
        assertTrue(afterOneSecond > start)
        assertTrue(afterOneSecond < 1f)
    }

    @Test
    fun invalidSensorReadingDoesNotChangeBrightness() {
        val controller = AutomaticBrightnessController(
            AutomaticBrightnessController.targetScaleForLux(100f)
        )
        val before = controller.scale

        assertEquals(before, controller.updateAmbientLux(Float.NaN, 1_000_000_000L), 0f)
        assertEquals(before, controller.updateAmbientLux(-1f, 2_000_000_000L), 0f)
    }
}
