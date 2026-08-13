package com.abdulkus.glyphlab.engine

import kotlin.math.roundToInt

/** Keeps the stock-like clock pixels unchanged until the displayed minute changes. */
class MinuteClockFrameCache {
    private var cachedMinute = Long.MIN_VALUE
    private var cachedBrightness = Float.NaN
    private var cachedFrame = IntArray(MatrixEngine.PIXEL_COUNT)

    @Synchronized
    fun frame(
        wallClockMillis: Long = System.currentTimeMillis(),
        masterBrightness: Float = 1f
    ): IntArray {
        val minute = wallClockMillis / MILLIS_PER_MINUTE
        val brightness = masterBrightness.coerceIn(0f, 1f)
        if (minute != cachedMinute || brightness != cachedBrightness) {
            cachedFrame = SleepClockRenderer.render(wallClockMillis).also { frame ->
                if (brightness != 1f) {
                    frame.indices.forEach { index ->
                        frame[index] = (frame[index] * brightness).roundToInt()
                    }
                }
            }
            cachedMinute = minute
            cachedBrightness = brightness
        }
        return cachedFrame
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
