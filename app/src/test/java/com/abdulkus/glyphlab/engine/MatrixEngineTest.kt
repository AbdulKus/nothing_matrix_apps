package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.data.SolidType
import org.junit.Assert.assertEquals
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
}
