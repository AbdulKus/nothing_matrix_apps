package com.abdulkus.glyphlab.engine

/** One cadence policy for the app, Always-On and settings. Carries fractional milliseconds. */
class FramePacer {
    private var nextNanos = 0L
    private var rate = 0

    fun delayMillis(frameRate: Int, nowNanos: Long = System.nanoTime()): Long {
        val fps = frameRate.coerceIn(8, 60)
        val period = 1_000_000_000L / fps
        if (rate != fps || nextNanos == 0L || nowNanos - nextNanos > period * 4) {
            nextNanos = nowNanos
            rate = fps
        }
        nextNanos += period
        return ((nextNanos - nowNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
    }

    fun reset() { nextNanos = 0L }

    companion object {
        val OPTIONS = listOf(12, 18, 24, 30, 45, 60)
        fun brightnessStepMillis(frameRate: Int): Long {
            val fps = frameRate.coerceIn(8, 60)
            return (1000L + fps - 1) / fps
        }
    }
}
