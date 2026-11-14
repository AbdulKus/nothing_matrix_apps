package com.abdulkus.glyphlab.engine

import kotlin.math.roundToInt
import com.abdulkus.glyphlab.data.ClockFont
import com.abdulkus.glyphlab.data.ClockLayout
import java.time.ZoneId

/** Keeps the stock-like clock pixels unchanged until the displayed minute changes. */
class MinuteClockFrameCache {
    private var cachedFont: ClockFont? = null
    private var cachedLayout: ClockLayout? = null
    private var cachedZone: ZoneId? = null
    private var cachedMinute = Long.MIN_VALUE
    private var cachedBrightness = Float.NaN
    private var cachedFrame = IntArray(MatrixEngine.PIXEL_COUNT)

    @Synchronized
    fun frame(
        wallClockMillis: Long = System.currentTimeMillis(),
        masterBrightness: Float = 1f,
        font: ClockFont = ClockFont.CLASSIC,
        layout: ClockLayout = ClockLayout.STACKED
    ): IntArray {
        val minute = wallClockMillis / MILLIS_PER_MINUTE
        val brightness = masterBrightness.coerceIn(0f, 1f)
        val zone = ZoneId.systemDefault()
        if (minute != cachedMinute || brightness != cachedBrightness || font != cachedFont || layout != cachedLayout || zone != cachedZone) {
            cachedFrame = ClockRenderer.render(wallClockMillis, font, layout).also { frame ->
                if (brightness != 1f) {
                    frame.indices.forEach { index ->
                        frame[index] = (frame[index] * brightness).roundToInt()
                    }
                }
            }
            cachedFont = font
            cachedLayout = layout
            cachedZone = zone
            cachedMinute = minute
            cachedBrightness = brightness
        }
        return cachedFrame
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
