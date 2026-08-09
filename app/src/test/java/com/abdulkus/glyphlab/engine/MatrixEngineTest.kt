package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.data.SolidType
import com.abdulkus.glyphlab.glyph.HardwareFrameMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixEngineTest {
    @Test
    fun everyEffectProducesValid13x13Frame() {
        EffectType.entries.forEachIndexed { index, effect ->
            val engine = MatrixEngine(seed = 100L + index)
            val config = MatrixConfig(effect = effect, brightness = 1f)
            var frame = IntArray(0)
            repeat(8) { tick ->
                frame = engine.render(config, 1_000_000_000L + tick * 41_000_000L, 0.2f, -0.15f)
            }
            assertEquals(MatrixEngine.PIXEL_COUNT, frame.size)
            assertTrue("$effect contains an invalid LED value", frame.all { it in 0..255 })
            assertTrue("$effect should light at least one LED", frame.any { it > 0 })
        }
    }

    @Test
    fun cornersOutsideCircularMatrixAreAlwaysDark() {
        val engine = MatrixEngine(42)
        EffectType.entries.forEach { effect ->
            val frame = engine.render(MatrixConfig(effect = effect), 2_000_000_000L)
            listOf(0, 12, 156, 168).forEach { corner -> assertEquals(0, frame[corner]) }
        }
    }

    @Test
    fun allSolidsRenderVisibleEdges() {
        SolidType.entries.forEach { solid ->
            val frame = MatrixEngine(7).render(
                MatrixConfig(effect = EffectType.WIREFRAME, solid = solid, brightness = 1f),
                3_000_000_000L
            )
            assertTrue("$solid should have a readable wireframe", frame.count { it > 0 } >= 10)
        }
    }

    @Test
    fun positiveAndroidYMovesSandTowardMatrixBottom() {
        fun renderWithTilt(tiltY: Float): IntArray {
            val engine = MatrixEngine(2026)
            val config = MatrixConfig(
                effect = EffectType.GRAVITY,
                accelerometer = true,
                sensorStrength = 1f,
                speed = 0.55f,
                trail = 0f,
                particleCount = 40,
                brightness = 1f
            )
            var frame = IntArray(MatrixEngine.PIXEL_COUNT)
            repeat(14) { tick ->
                frame = engine.render(
                    config,
                    4_000_000_000L + tick * 41_000_000L,
                    tiltX = 0f,
                    tiltY = tiltY
                )
            }
            return frame
        }

        fun verticalCenter(frame: IntArray): Double {
            val total = frame.sum().coerceAtLeast(1)
            return frame.indices.sumOf { index -> (index / MatrixEngine.SIZE) * frame[index] }
                .toDouble() / total
        }

        assertTrue(verticalCenter(renderWithTilt(0.8f)) > verticalCenter(renderWithTilt(-0.8f)))
    }

    @Test
    fun zeroSpeedKeepsWireframeStill() {
        val engine = MatrixEngine(9)
        val config = MatrixConfig(
            effect = EffectType.WIREFRAME,
            solid = SolidType.CUBE,
            speed = 0f,
            accelerometer = false,
            brightness = 1f
        )

        val first = engine.render(config, 1_000_000_000L)
        val muchLater = engine.render(config, 20_000_000_000L)
        assertTrue(first.contentEquals(muchLater))
    }

    @Test
    fun tiltChangesWireframeViewWithCameraParallax() {
        val config = MatrixConfig(
            effect = EffectType.WIREFRAME,
            solid = SolidType.CUBE,
            speed = 0f,
            accelerometer = true,
            sensorStrength = 1f,
            brightness = 1f
        )
        val leftView = MatrixEngine(11).render(config, 1_000_000_000L, tiltX = -0.75f)
        val rightView = MatrixEngine(11).render(config, 1_000_000_000L, tiltX = 0.75f)

        assertFalse(leftView.contentEquals(rightView))
        assertTrue(leftView.count { it > 0 } >= 10)
        assertTrue(rightView.count { it > 0 } >= 10)
    }

    @Test
    fun glyphMapperBrightensMidtonesWithoutExceedingSdkRange() {
        val mapped = HardwareFrameMapper.forGlyph(intArrayOf(0, 32, 128, 255))

        assertEquals(0, mapped[0])
        assertTrue(mapped[1] > 32)
        assertTrue(mapped[2] > 128)
        assertEquals(255, mapped[3])
        assertTrue(mapped.zipWithNext().all { (a, b) -> a <= b })
    }
}
