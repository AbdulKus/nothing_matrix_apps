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
    fun negativeNormalizedYMovesSandTowardMatrixBottom() {
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

        assertTrue(verticalCenter(renderWithTilt(-0.8f)) > verticalCenter(renderWithTilt(0.8f)))
    }

    @Test
    fun positiveNormalizedXDeflectsFireTowardMatrixRight() {
        fun renderWithTilt(tiltX: Float): IntArray {
            val engine = MatrixEngine(606)
            val config = MatrixConfig(
                effect = EffectType.FIRE,
                accelerometer = true,
                sensorStrength = 1f,
                speed = 0.7f,
                intensity = 0.9f,
                brightness = 1f
            )
            var frame = IntArray(MatrixEngine.PIXEL_COUNT)
            repeat(28) { tick ->
                frame = engine.render(
                    config,
                    2_000_000_000L + tick * 41_000_000L,
                    tiltX = tiltX,
                    tiltY = 0f
                )
            }
            return frame
        }

        fun horizontalCenter(frame: IntArray): Double {
            val total = frame.sum().coerceAtLeast(1)
            return frame.indices.sumOf { index -> (index % MatrixEngine.SIZE) * frame[index] }
                .toDouble() / total
        }

        assertTrue(horizontalCenter(renderWithTilt(0.9f)) > horizontalCenter(renderWithTilt(-0.9f)))
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
    fun glyphMapperUsesFullTwelveBitRange() {
        val mapped = HardwareFrameMapper.forGlyph(intArrayOf(0, 32, 128, 255), 1f)

        assertEquals(0, mapped[0])
        assertTrue(mapped[1] > 32)
        assertTrue(mapped[2] > 128)
        assertEquals(4095, mapped[3])
        assertTrue(mapped.all { it in 0..4095 })
        assertTrue(mapped.indices.drop(1).all { index -> mapped[index - 1] <= mapped[index] })
    }

    @Test
    fun glyphToyCompensatesOnlyTheSystemAodOutputPath() {
        val frame = intArrayOf(0, 32, 128, 255)
        val app = HardwareFrameMapper.forGlyph(frame, 1f)
        val toy = HardwareFrameMapper.forGlyphToy(frame, 1f)

        assertEquals(0, toy[0])
        assertTrue(toy.indices.drop(1).all { index ->
            kotlin.math.abs(toy[index] - app[index] * 4) <= 2
        })
    }

    @Test
    fun glyphMapperPreservesMasterBrightnessRange() {
        val tenPercentInput = intArrayOf(0, 3, 13, 26)
        val mapped = HardwareFrameMapper.forGlyph(tenPercentInput, 0.1f)

        assertEquals(0, mapped[0])
        assertTrue(mapped.max() <= 410)
        assertEquals(410, mapped.last())
        assertTrue(HardwareFrameMapper.forGlyph(intArrayOf(255), 0f).all { it == 0 })
    }

    @Test
    fun automaticScaleChangesOnlyPhysicalOutputBrightness() {
        val frame = intArrayOf(0, 32, 128, 255)
        val full = HardwareFrameMapper.forGlyph(frame, 1f)
        val dimmed = HardwareFrameMapper.forGlyph(frame, 1f, automaticScale = 0.5f)

        assertEquals(0, dimmed[0])
        assertTrue(dimmed.indices.drop(1).all { index ->
            kotlin.math.abs(dimmed[index] - full[index] * 0.5f) <= 1f
        })
        assertTrue(frame.contentEquals(intArrayOf(0, 32, 128, 255)))
    }

    @Test
    fun automaticBrightnessInterpolatesBetweenConfiguredMinimumAndMaximum() {
        val frame = intArrayOf(255)
        val minimum = HardwareFrameMapper.forGlyph(
            frame,
            masterBrightness = 1f,
            automaticScale = 0f,
            minimumBrightness = 0.2f
        ).single()
        val middle = HardwareFrameMapper.forGlyph(
            frame,
            masterBrightness = 1f,
            automaticScale = 0.5f,
            minimumBrightness = 0.2f
        ).single()
        val maximum = HardwareFrameMapper.forGlyph(
            frame,
            masterBrightness = 1f,
            automaticScale = 1f,
            minimumBrightness = 0.2f
        ).single()

        assertEquals(819, minimum)
        assertEquals(2457, middle)
        assertEquals(4095, maximum)
    }

    @Test
    fun cubeKeepsFullExpressiveWireframeAtMatrixResolution() {
        val frame = MatrixEngine(31).render(
            MatrixConfig(
                effect = EffectType.WIREFRAME,
                solid = SolidType.CUBE,
                speed = 0f,
                accelerometer = false,
                brightness = 1f
            ),
            1_000_000_000L
        )

        assertTrue(frame.count { it > 0 } in 24..60)
        assertTrue(frame.filter { it > 0 }.distinct().size >= 4)
    }

    @Test
    fun disabledAutoRotationAxesKeepCameraStillAtNonZeroSpeed() {
        val config = MatrixConfig(
            effect = EffectType.WIREFRAME,
            solid = SolidType.CUBE,
            speed = 1f,
            autoRotateX = false,
            autoRotateY = false,
            autoRotateZ = false,
            accelerometer = false,
            brightness = 1f
        )
        val first = MatrixEngine(41).render(config, 1_000_000_000L)
        val later = MatrixEngine(41).render(config, 8_000_000_000L)

        assertTrue(first.contentEquals(later))
    }

    @Test
    fun everySelectedAutoRotationAxisAnimatesIndependently() {
        listOf("X", "Y", "Z").forEach { axis ->
            val config = MatrixConfig(
                effect = EffectType.WIREFRAME,
                solid = SolidType.CUBE,
                speed = 0.7f,
                autoRotateX = axis == "X",
                autoRotateY = axis == "Y",
                autoRotateZ = axis == "Z",
                accelerometer = false,
                brightness = 1f
            )
            val first = MatrixEngine(42).render(config, 1_000_000_000L)
            val later = MatrixEngine(42).render(config, 3_700_000_000L)

            assertFalse("Axis $axis should animate", first.contentEquals(later))
        }
    }

    @Test
    fun sandKeepsEveryGrainInsideCircularMatrix() {
        val engine = MatrixEngine(77)
        val config = MatrixConfig(
            effect = EffectType.GRAVITY,
            accelerometer = true,
            sensorStrength = 1f,
            speed = 1f,
            particleCount = 40,
            brightness = 1f
        )
        var frame = IntArray(MatrixEngine.PIXEL_COUNT)
        repeat(80) { tick ->
            frame = engine.render(
                config,
                1_000_000_000L + tick * 50_000_000L,
                tiltX = 0.9f,
                tiltY = 0.7f
            )
        }

        assertEquals(40, frame.count { it > 0 })
        frame.indices.filter { frame[it] > 0 }.forEach { index ->
            assertTrue(MatrixEngine.isInsideMatrix(index % MatrixEngine.SIZE, index / MatrixEngine.SIZE))
        }
    }

    @Test
    fun zeroBrightnessReallyTurnsEveryLedOff() {
        val frame = MatrixEngine(88).render(
            MatrixConfig(effect = EffectType.WIREFRAME, brightness = 0f),
            1_000_000_000L
        )

        assertTrue(frame.all { it == 0 })
    }
}
