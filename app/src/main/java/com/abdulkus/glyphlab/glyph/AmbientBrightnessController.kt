package com.abdulkus.glyphlab.glyph

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Converts ambient illuminance to a stable physical-output multiplier.
 *
 * Brightness perception is logarithmic, so fixed lux thresholds produce harsh
 * jumps. The curve is interpolated in log-lux space and then time-smoothed:
 * brightening is quick for visibility, while dimming is slower to avoid flicker.
 */
class AmbientBrightnessController(initialLux: Float? = null) {
    @Volatile
    var scale: Float = initialLux?.let(::targetScaleForLux) ?: 1f
        private set

    private var lastTimestampNanos = 0L

    fun updateLux(lux: Float, timestampNanos: Long): Float {
        if (!lux.isFinite() || lux < 0f) return scale
        val target = targetScaleForLux(lux)
        if (lastTimestampNanos == 0L || timestampNanos <= lastTimestampNanos) {
            scale = target
            lastTimestampNanos = timestampNanos
            return scale
        }

        val elapsedSeconds = ((timestampNanos - lastTimestampNanos) / 1_000_000_000.0)
            .coerceIn(0.0, 5.0)
        lastTimestampNanos = timestampNanos
        if (abs(target - scale) < DEADBAND) return scale

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
        private val SCALE_POINTS = floatArrayOf(0.07f, 0.09f, 0.14f, 0.22f, 0.38f, 0.62f, 0.82f, 1f)

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
    }
}
