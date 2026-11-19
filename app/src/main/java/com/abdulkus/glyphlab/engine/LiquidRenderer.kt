package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.MatrixConfig
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** A damped free surface in a circular vessel, rendered with subpixel coverage. */
internal class LiquidRenderer {
    private var downAngle = 0.0
    private var surfaceAngle = 0.0
    private var angularVelocity = 0.0
    private var ripple = 0.0
    private var rippleVelocity = 0.0
    private val depths = DoubleArray(MatrixEngine.PIXEL_COUNT)

    private val pixels = depths.indices.filter { MatrixEngine.isInsideMatrix(it % MatrixEngine.SIZE, it / MatrixEngine.SIZE) }

    fun render(config: MatrixConfig, dt: Float, gx: Float, gy: Float): IntArray {
        val gravity = hypot(gx, gy)
        val followsTilt = config.accelerometer && config.sensorStrength > 0f
        val oldDown = downAngle
        if (!followsTilt) downAngle = 0.0
        else if (gravity > 0.055f * config.sensorStrength) downAngle = atan2(gx.toDouble(), gy.toDouble())

        val viscosity = config.liquidViscosity.coerceIn(0f, 1f)
        val turn = wrap(downAngle - oldDown)
        rippleVelocity += turn * 5.5 * (1 - viscosity * 0.75)
        val steps = ceil(dt * 120).toInt().coerceAtLeast(1)
        val step = dt.toDouble() / steps
        repeat(steps) {
            val stiffness = 46 + gravity * 38
            angularVelocity += (wrap(downAngle - surfaceAngle) * stiffness -
                angularVelocity * (3.5 + viscosity * 15)) * step
            surfaceAngle = wrap(surfaceAngle + angularVelocity * step)
            rippleVelocity += (-ripple * 115 - rippleVelocity * (2.8 + viscosity * 19)) * step
            ripple = (ripple + rippleVelocity * step).coerceIn(-1.2, 1.2)
        }

        val dx = sin(surfaceAngle)
        val dy = cos(surfaceAngle)
        val center = (MatrixEngine.SIZE - 1) / 2.0
        for (i in depths.indices) {
            val x = i % MatrixEngine.SIZE - center
            val y = i / MatrixEngine.SIZE - center
            val alongSurface = x * dy - y * dx
            depths[i] = x * dx + y * dy +
                ripple * sin(alongSurface * 0.92) +
                angularVelocity.coerceIn(-8.0, 8.0) * 0.08 * cos(alongSurface * 0.62)
        }

        // Find the surface height that preserves volume at every angle.
        val fill = 0.15 + config.liquidFill.coerceIn(0f, 1f) * 0.70
        var low = -10.0
        var high = 10.0
        repeat(12) {
            val middle = (low + high) / 2
            val volume = pixels.sumOf { coverage(depths[it] - middle) }
            if (volume > pixels.size * fill) low = middle else high = middle
        }
        val level = (low + high) / 2
        return IntArray(depths.size).also { frame ->
            for (i in pixels) {
                val depth = depths[i] - level
                val highlight = (1 - depth.coerceIn(0.0, 2.0) / 2) * 110
                frame[i] = (coverage(depth) * (137 + highlight)).roundToInt()
            }
        }
    }

    private fun coverage(depth: Double) = (depth + 0.5).coerceIn(0.0, 1.0)

    private fun wrap(angle: Double): Double = when {
        angle > PI -> angle - 2 * PI
        angle < -PI -> angle + 2 * PI
        else -> angle
    }
}
