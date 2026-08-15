package com.abdulkus.glyphlab.glyph

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Smooths automatic brightness from either ambient lux or screen brightness. */
class AutomaticBrightnessController(initialScale: Float = 1f) {
    @Volatile
    var scale: Float = initialScale.coerceIn(0f, 1f)
        private set

    val isTransitioning: Boolean
        @Synchronized get() {
            val tolerance = if (snapInsideDeadband) SETTLED_TOLERANCE else DEADBAND
            return abs(targetScale - scale) >= tolerance
        }

    private var lastTimestampNanos = 0L
    private var targetScale = scale
    private var snapInsideDeadband = false

    private val ambientSamples = FloatArray(AMBIENT_MEDIAN_WINDOW)
    private var ambientSampleCount = 0
    private var ambientSampleIndex = 0
    private var filteredAmbientLogLux: Double? = null
    private var lastAmbientTimestampNanos = 0L

    @Synchronized
    fun updateAmbientLux(lux: Float, timestampNanos: Long): Float {
        if (!lux.isFinite() || lux < 0f) return scale

        val filteredLux = filterAmbientLuxLocked(lux, timestampNanos)
        val nextTarget = targetScaleForLux(filteredLux)

        // The light sensor is especially noisy close to darkness. Do not chase
        // tiny target changes there; wait until they add up to something visible.
        if (ambientSampleCount == 1 || abs(nextTarget - targetScale) >= AMBIENT_TARGET_HYSTERESIS) {
            targetScale = nextTarget
        }

        snapInsideDeadband = false
        return advanceLocked(timestampNanos)
    }

    @Synchronized
    fun updateScreenBrightness(brightness: Int, timestampNanos: Long): Float {
        targetScale = targetScaleForScreenBrightness(brightness)
        snapInsideDeadband = true
        return advanceLocked(timestampNanos)
    }

    @Synchronized
    fun advance(timestampNanos: Long): Float = advanceLocked(timestampNanos)

    private fun filterAmbientLuxLocked(lux: Float, timestampNanos: Long): Float {
        ambientSamples[ambientSampleIndex] = lux
        ambientSampleIndex = (ambientSampleIndex + 1) % AMBIENT_MEDIAN_WINDOW
        if (ambientSampleCount < AMBIENT_MEDIAN_WINDOW) ambientSampleCount++

        val sorted = FloatArray(ambientSampleCount) { ambientSamples[it] }.apply { sort() }
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) * 0.5f
        }

        // Filter in log-lux space because a 1 -> 3 lux change is much more
        // meaningful than 501 -> 503 lux. This keeps night readings calm while
        // still reacting promptly when the room genuinely gets brighter.
        val measuredLogLux = ln(median.toDouble() + 1.0)
        val previousLogLux = filteredAmbientLogLux
        if (
            previousLogLux == null ||
            lastAmbientTimestampNanos == 0L ||
            timestampNanos <= lastAmbientTimestampNanos
        ) {
            filteredAmbientLogLux = measuredLogLux
            lastAmbientTimestampNanos = timestampNanos
            return median
        }

        val elapsedSeconds = ((timestampNanos - lastAmbientTimestampNanos) / 1_000_000_000.0)
            .coerceIn(0.0, 2.0)
        lastAmbientTimestampNanos = timestampNanos
        val timeConstant = if (measuredLogLux > previousLogLux) {
            AMBIENT_FILTER_BRIGHTEN_TIME_SECONDS
        } else {
            AMBIENT_FILTER_DIM_TIME_SECONDS
        }
        val alpha = 1.0 - exp(-elapsedSeconds / timeConstant)
        val filteredLogLux = previousLogLux + (measuredLogLux - previousLogLux) * alpha
        filteredAmbientLogLux = filteredLogLux
        return (exp(filteredLogLux) - 1.0).toFloat().coerceAtLeast(0f)
    }

    private fun advanceLocked(timestampNanos: Long): Float {
        // Never snap to the first sensor reading. A cold light sensor can report
        // a transient value, which used to create a very visible one-frame flash.
        if (lastTimestampNanos == 0L) {
            lastTimestampNanos = timestampNanos
            return scale
        }
        if (timestampNanos <= lastTimestampNanos) return scale

        val elapsedSeconds = ((timestampNanos - lastTimestampNanos) / 1_000_000_000.0)
            .coerceIn(0.0, 5.0)
        lastTimestampNanos = timestampNanos
        if (abs(targetScale - scale) < DEADBAND) {
            if (snapInsideDeadband) scale = targetScale
            return scale
        }

        val timeConstant = if (targetScale > scale) BRIGHTEN_TIME_SECONDS else DIM_TIME_SECONDS
        val alpha = (1.0 - exp(-elapsedSeconds / timeConstant)).toFloat()
        scale += (targetScale - scale) * alpha
        return scale
    }

    companion object {
        private const val DEADBAND = 0.006f
        private const val SETTLED_TOLERANCE = 0.003f
        private const val BRIGHTEN_TIME_SECONDS = 0.65
        private const val DIM_TIME_SECONDS = 1.35

        private const val AMBIENT_MEDIAN_WINDOW = 3
        private const val AMBIENT_TARGET_HYSTERESIS = 0.008f
        private const val AMBIENT_FILTER_BRIGHTEN_TIME_SECONDS = 0.30
        private const val AMBIENT_FILTER_DIM_TIME_SECONDS = 0.55

        // Keep the bottom of the curve deliberately compressed. Glyph Toy output
        // receives additional AOD gain later, so the old 2/8/16% targets at
        // 1/5/20 lux became disproportionately bright in a dark room.
        private val LUX_POINTS = floatArrayOf(0f, 1f, 5f, 20f, 100f, 500f, 2_000f, 10_000f)
        private val SCALE_POINTS = floatArrayOf(0f, 0.005f, 0.018f, 0.045f, 0.12f, 0.30f, 0.58f, 1f)

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
