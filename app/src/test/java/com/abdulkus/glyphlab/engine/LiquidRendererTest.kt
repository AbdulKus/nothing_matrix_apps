package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidRendererTest {
    @Test
    fun settlesTowardAllFourPhysicalEdges() {
        val config = MatrixConfig(effect = EffectType.LIQUID, brightness = 1f, liquidViscosity = 0.7f)
        val engine = MatrixEngine()
        var time = 1L
        fun settle(androidX: Float, androidY: Float): IntArray {
            val (x, y) = SensorTilt.fromAndroid(androidX, androidY)
            var frame = IntArray(0)
            repeat(120) {
                time += 16_666_667L
                frame = engine.render(config, time, x, y)
            }
            return frame
        }

        val top = 2 * MatrixEngine.SIZE + 6
        val bottom = 10 * MatrixEngine.SIZE + 6
        val left = 6 * MatrixEngine.SIZE + 2
        val right = 6 * MatrixEngine.SIZE + 10
        val upright = settle(0f, 9.81f)
        val upsideDown = settle(0f, -9.81f)
        val rightSideDown = settle(9.81f, 0f)
        val leftSideDown = settle(-9.81f, 0f)
        assertTrue(upright[bottom] > upright[top])
        assertTrue(upsideDown[top] > upsideDown[bottom])
        assertTrue(rightSideDown[right] > rightSideDown[left])
        assertTrue(leftSideDown[left] > leftSideDown[right])
    }

    @Test
    fun increasingFillLightsMorePixels() {
        val renderer = LiquidRenderer()
        val config = MatrixConfig()
        val low = renderer.render(config.copy(liquidFill = 0f), 1f / 60, 0f, 1f)
        val high = renderer.render(config.copy(liquidFill = 1f), 1f / 60, 0f, 1f)
        assertTrue(high.count { it > 0 } > low.count { it > 0 } * 3)
    }
}
