package com.abdulkus.glyphlab.glyph

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Smooths automatic brightness from either ambient lux or screen brightness. */
class AutomaticBrightnessController(initialScale: Float = 1f) {
    @Volatile
    var scale: Float = initialScale.coerceIn(0f, 1f)
        private set

    private var lastTimestampNanos = 0L

    fun updateAmbientLux(lux: Float, timestampNanos: Long): Float {
        if (!lux.isFinite() || lux < 0f) return scale
        return updateTarget(targetScaleForLux(lux), timestampNanos, snapInsideDeadband = false)
    }

    fun updateScreenBrightness(brightness: Int, timestampNanos: Long): Float =
        updateTarget(
            targetScaleForScreenBrightness(brightness),
            timestampNanos,
            snapInsideDeadband = true
        )

    private fun updateTarget(
        target: Float,
        timestampNanos: Long,
        snapInsideDeadband: Boolean
    ): Float {
        if (lastTimestampNanos == 0L || timestampNanos <= lastTimestampNanos) {
            scale = target
            lastTimestampNanos = timestampNanos
            return scale
        }

        val elapsedSeconds = ((timestampNanos - lastTimestampNanos) / 1_000_000_000.0)
            .coerceIn(0.0, 5.0)
        lastTimestampNanos = timestampNanos
        if (abs(target - scale) < DEADBAND) {
            if (snapInsideDeadband) scale = target
            return scale
        }

        val timeConstant = if (target > scale) BRIGHTEN_TIME_SECONDS else DIM_TIME_SECONDS
        val alpha = (1.0 - exp(-elapsedSeconds / timeConstant)).toFloat()
        scale += (target - scale) * alpha
        return scale
    }

    companion object {
        private const val DEADBAND = 0.025f
        private const val BRIGHTEN_TIME_SECONDS = 0.8
        private const val DIM_TIME_SECONDS = 2.5

        private val LUX_POINTS = floatArrayOf(0f, 1f, 5f, 20f, 100f, 500f, 2_000f, 10_000f)
        private val SCALE_POINTS = floatArrayOf(0f, 0.02f, 0.08f, 0.16f, 0.33f, 0.59f, 0.81f, 1f)

        fun targetScaleForLux(lux: Float): Float {
            val value = lux.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            if (value <= LUX_POINTS.first()) return SCALE_POINTS.first()
            if (value >= LUX_POINTS.last()) return SCALE_POINTS.last()

            val upper = LUX_POINTS.indexOfFirst { value <= it }
            val lower = upper - 1
            val logValue = ln(value + 1f)
            val logLower = ln(LUX_POINTS[lower] + 1f)
            val logUpper = ln(LUX_POINTS[upper] + 1f)
            val position = ((logValue - logLower) / (logUpper - logLower)).coerceIn(0f, 1f)
            return SCALE_POINTS[lower] + (SCALE_POINTS[upper] - SCALE_POINTS[lower]) * position
        }

        fun targetScaleForScreenBrightness(brightness: Int): Float {
            return brightness.coerceIn(0, 255) / 255f
        }
    }
}
