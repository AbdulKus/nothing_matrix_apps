package com.abdulkus.glyphlab.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/** Confirms a stable pose before moving pixels; no interpolation of the image or delayed pulse. */
class TiltStabilizer {
    data class Tilt(val x: Float, val y: Float)
    private val horizontal = Axis()
    private val vertical = Axis()
    private var lastUpdate = Long.MIN_VALUE

    fun reset() { horizontal.reset(); vertical.reset(); lastUpdate = Long.MIN_VALUE }

    fun update(x: Float, y: Float, nowNanos: Long): Tilt {
        if (lastUpdate != Long.MIN_VALUE && (nowNanos < lastUpdate || nowNanos - lastUpdate > MAX_SAMPLE_GAP_NS)) {
            horizontal.cancelPending(); vertical.cancelPending()
        }
        lastUpdate = nowNanos
        return Tilt(horizontal.update(x, nowNanos), vertical.update(y, nowNanos))
    }

    private class Axis {
        private var accepted = 0
        private var pending: Int? = null
        private var since = 0L
        fun reset() { accepted = 0; cancelPending() }
        fun cancelPending() { pending = null }
        fun update(input: Float, now: Long): Float {
            val value = if (input.isFinite()) input.coerceIn(-1f, 1f) else 0f
            // Hysteresis around both the accepted and pending pose prevents boundary chatter.
            if (abs(value - accepted * STEP) <= STEP / 2 + HYSTERESIS) {
                cancelPending()
                return accepted * STEP
            }
            val proposed = pending?.takeIf { abs(value - it * STEP) <= STEP / 2 + HYSTERESIS }
                ?: (value / STEP).roundToInt().coerceIn(-8, 8)
            if (pending != proposed) {
                pending = proposed
                since = now
            } else if (now - since >= CONFIRM_NS) {
                accepted = proposed
                cancelPending()
            }
            return accepted * STEP
        }
    }

    companion object {
        private const val STEP = 0.125f
        private const val HYSTERESIS = 0.025f
        private const val CONFIRM_NS = 140_000_000L
        private const val MAX_SAMPLE_GAP_NS = 250_000_000L
    }
}
