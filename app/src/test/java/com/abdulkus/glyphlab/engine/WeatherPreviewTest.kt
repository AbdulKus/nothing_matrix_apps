package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.glyph.MatrixOutputMode
import com.abdulkus.glyphlab.glyph.resolveMatrixOutputMode
import com.abdulkus.glyphlab.weather.WeatherCondition
import com.abdulkus.glyphlab.weather.WeatherPreview
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WeatherPreviewTest {
    @After fun closePreview() { WeatherPreview.clear() }

    @Test fun selectionPublishesTheRealRendererFrameAndRejectsOutdatedFrames() {
        WeatherPreview.show(WeatherCondition.SNOW)
        val frame = WeatherRenderer.render(WeatherCondition.SNOW, 2.0, tiltX = 0.8f)
        WeatherPreview.publish(WeatherCondition.SNOW, frame)
        assertSame(frame, WeatherPreview.state.value.frame)
        WeatherPreview.show(WeatherCondition.RAIN)
        val selectedFrame = WeatherPreview.state.value.frame
        WeatherPreview.publish(WeatherCondition.SNOW, frame)
        assertSame(selectedFrame, WeatherPreview.state.value.frame)
        assertEquals(WeatherCondition.RAIN, WeatherPreview.state.value.condition)
        WeatherPreview.clear()
        WeatherPreview.publish(WeatherCondition.RAIN, frame)
        assertNull(WeatherPreview.state.value.condition)
        assertTrue(WeatherPreview.state.value.frame.all { it == 0 })
    }

    @Test fun manualPreviewReachesAodEvenWithSleepClockAndRestoresItsPolicyOnClose() {
        val config = MatrixConfig(effect = EffectType.WEATHER, sleepClockEnabled = true)
        WeatherPreview.show(WeatherCondition.HAIL)
        assertEquals(MatrixOutputMode.EFFECT, resolveMatrixOutputMode(config, false, true,
            weatherPreviewActive = WeatherPreview.state.value.condition != null))
        WeatherPreview.clear()
        assertEquals(MatrixOutputMode.CLOCK, resolveMatrixOutputMode(config, false, true,
            weatherPreviewActive = WeatherPreview.state.value.condition != null))
        assertEquals(EffectType.WEATHER, config.effect)
    }

    @Test fun disablingTiltOrSettingZeroStrengthKeepsWeatherStillWhenPhoneMoves() {
        val engine = MatrixEngine()
        val config = MatrixConfig(effect = EffectType.WEATHER, accelerometer = false, brightness = 1f)
        val time = 3_000_000_000L
        val baseline = engine.render(config, time, weather = WeatherCondition.RAIN)
        assertArrayEquals(baseline, engine.render(config, time, 1f, -0.5f, WeatherCondition.RAIN))
        assertArrayEquals(baseline, engine.render(config.copy(accelerometer = true, sensorStrength = 0f), time, 1f, -0.5f, WeatherCondition.RAIN))
        val enabled = engine.render(config.copy(accelerometer = true, sensorStrength = 1f), time, 1f, -0.5f, WeatherCondition.RAIN)
        assertFalse(baseline.contentEquals(enabled))
    }

    @Test fun tiltPreservesWholeHailstonesAndSnowflakesAndTheCircularMask() {
        listOf(-1f, 0f, 1f).forEach { tilt ->
            WeatherCondition.entries.forEach { condition -> repeat(360) { tick ->
                val frame = WeatherRenderer.render(condition, tick / 10.0, tilt, -1f)
                frame.forEachIndexed { i, v ->
                    assertTrue(v in 0..255)
                    if (!MatrixEngine.isInsideMatrix(i % 13, i / 13)) assertEquals(0, v)
                }
                if (condition == WeatherCondition.HAIL) assertEquals(8, frame.drop(7 * 13).count { it > 0 })
                if (condition == WeatherCondition.SNOW || condition == WeatherCondition.HEAVY_SNOW) {
                    assertEquals(0, frame.drop(7 * 13).count { it > 0 } % 5)
                    assertTrue((0..12).all { frame[6 * 13 + it] == 0 })
                }
            } }
        }
    }
}
