package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test

class TiltStabilizerTest {
    @Test fun transientMotionDoesNotCreateAnImmediateOrDelayedMove() {
        listOf(12, 24, 45, 60).forEach { fps ->
            val guard = TiltStabilizer()
            val step = 1_000_000_000L / fps
            repeat(fps * 2) { tick ->
                val spike = if (tick == fps / 2) 1f else 0f
                val pose = guard.update(spike, -spike, 1_000_000_000L + tick * step)
                assertEquals("one-frame spike at $fps FPS", 0f, pose.x, 0f)
                assertEquals(0f, pose.y, 0f)
            }
        }
    }

    @Test fun sustainedTiltIsConfirmedAndBoundaryNoiseCannotFlipItBack() {
        val guard = TiltStabilizer()
        assertEquals(0f, guard.update(0.5f, -0.5f, 1_000_000_000L).x, 0f)
        assertEquals(0f, guard.update(0.5f, -0.5f, 1_120_000_000L).x, 0f)
        val confirmed = guard.update(0.5f, -0.5f, 1_150_000_000L)
        assertEquals(0.5f, confirmed.x, 0f)
        assertEquals(-0.5f, confirmed.y, 0f)
        repeat(120) { tick ->
            val noise = if (tick % 2 == 0) 0.44f else 0.50f
            assertEquals(0.5f, guard.update(noise, -noise, 1_160_000_000L + tick * 10_000_000L).x, 0f)
        }
    }

    @Test fun aPauseDoesNotCountAsEvidenceOfStableTilt() {
        val guard = TiltStabilizer()
        guard.update(1f, 0f, 1_000_000_000L)
        assertEquals(0f, guard.update(1f, 0f, 9_000_000_000L).x, 0f)
        assertEquals(1f, guard.update(1f, 0f, 9_150_000_000L).x, 0f)
        guard.reset()
        assertEquals(0f, guard.update(1f, 0f, 9_160_000_000L).x, 0f)
    }

    @Test fun actualWeatherFramesStayUnchangedDuringBriefSpikesAndMoveAfterStableTilt() {
        WeatherCondition.entries.forEach { condition ->
            val engine = MatrixEngine()
            val config = MatrixConfig(effect = EffectType.WEATHER, accelerometer = true, sensorStrength = 1f, brightness = 1f)
            val first = engine.render(config, 1_000_000_000L, weather = condition, weatherSecondsOverride = 2.0)
            repeat(60) { tick ->
                val spike = if (tick == 10) 1f else 0f
                val frame = engine.render(config, 1_020_000_000L + tick * 20_000_000L, spike, -spike, condition, 2.0)
                assertArrayEquals("$condition twitch at frame $tick", first, frame)
            }
            if (condition in listOf(WeatherCondition.RAIN, WeatherCondition.CLOUDY, WeatherCondition.SUNNY, WeatherCondition.CLEAR_NIGHT, WeatherCondition.HAIL)) {
                var moved = first
                repeat(16) { tick ->
                    moved = engine.render(config, 2_240_000_000L + tick * 20_000_000L, 1f, -1f, condition, 2.0)
                }
                assertFalse("$condition does not respond to sustained tilt", first.contentEquals(moved))
            }
        }
    }
}
