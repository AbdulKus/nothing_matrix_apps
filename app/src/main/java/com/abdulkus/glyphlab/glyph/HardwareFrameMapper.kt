package com.abdulkus.glyphlab.glyph

import kotlin.math.pow
import kotlin.math.roundToInt

/** Maps preview luminance to the brighter response needed by the physical LEDs. */
object HardwareFrameMapper {
    private const val OUTPUT_GAMMA = 0.70

    fun forGlyph(frame: IntArray, masterBrightness: Float): IntArray {
        val master = masterBrightness.coerceIn(0f, 1f).toDouble()
        if (master == 0.0) return IntArray(frame.size)

        return IntArray(frame.size) { index ->
            val input = frame[index].coerceIn(0, 255)
            if (input == 0) {
                0
            } else {
                // MatrixEngine has already applied master brightness. Recover the
                // source level, shape its midtones, then apply master once at the
                // very end. This keeps 10% actually near 10% instead of the old
                // gamma curve turning it into roughly 35%.
                val source = (input / 255.0 / master).coerceIn(0.0, 1.0)
                (255.0 * source.pow(OUTPUT_GAMMA) * master)
                    .roundToInt().coerceIn(1, 255)
            }
        }
    }
}
