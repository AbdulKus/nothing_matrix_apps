package com.abdulkus.glyphlab.glyph

import kotlin.math.pow
import kotlin.math.roundToInt

/** Maps preview luminance to the brighter response needed by the physical LEDs. */
object HardwareFrameMapper {
    private const val OUTPUT_GAMMA = 0.45

    fun forGlyph(frame: IntArray): IntArray = IntArray(frame.size) { index ->
        val input = frame[index].coerceIn(0, 255)
        if (input == 0) {
            0
        } else {
            (255.0 * (input / 255.0).pow(OUTPUT_GAMMA)).roundToInt().coerceIn(1, 255)
        }
    }
}
