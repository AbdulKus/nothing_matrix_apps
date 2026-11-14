package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import org.junit.Assert.*
import org.junit.Test

class FramePacerTest {
    @Test fun requestedCadenceCarriesRoundingAndAccountsForRenderingTime() {
        FramePacer.OPTIONS.forEach { fps ->
            val pacer = FramePacer()
            val start = 1_000_000_000L
            var now = start
            repeat(fps * 10) {
                now += 3_000_000L // Simulated render/IPC work, not an extra delay per frame.
                now += pacer.delayMillis(fps, now) * 1_000_000L
            }
            assertTrue("$fps FPS drifted over ten seconds: ${now - start}", now - start in 10_000_000_000L..10_005_000_000L)
        }
    }

    @Test fun rateChangesAndLongPausesRestartWithoutFrameBursts() {
        val pacer = FramePacer()
        pacer.delayMillis(12, 1_000_000_000L)
        assertEquals(17L, pacer.delayMillis(60, 1_010_000_000L))
        assertEquals(17L, pacer.delayMillis(60, 9_000_000_000L))
        pacer.reset()
        assertEquals(23L, pacer.delayMillis(45, 9_001_000_000L))
    }

    @Test fun everyEffectAcceptsFortyFiveAndSixtyFps() {
        listOf(45, 60).forEach { fps -> EffectType.entries.forEach { effect ->
            val engine = MatrixEngine(45)
            repeat(60) { tick ->
                val frame = engine.render(MatrixConfig(effect = effect, frameRate = fps), 1_000_000_000L + tick * (1_000_000_000L / fps))
                assertEquals(169, frame.size)
                assertTrue(frame.all { it in 0..255 })
            }
        } }
    }
}
