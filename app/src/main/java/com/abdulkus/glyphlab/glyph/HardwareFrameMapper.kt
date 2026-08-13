package com.abdulkus.glyphlab.glyph

import kotlin.math.pow
import kotlin.math.roundToInt

/** Maps 8-bit preview luminance to the Glyph Matrix's 12-bit LED levels. */
object HardwareFrameMapper {
    private const val PREVIEW_MAX = 255.0
    private const val MATRIX_MAX = 4095
    private const val OUTPUT_GAMMA = 0.70
    private const val TOY_AOD_GAIN = 4.0

    fun forGlyph(
        frame: IntArray,
        masterBrightness: Float,
        ambientScale: Float = 1f
    ): IntArray {
        val master = masterBrightness.coerceIn(0f, 1f).toDouble()
        if (master == 0.0) return IntArray(frame.size)

        return map(frame, master, ambientScale, outputGain = 1.0)
    }

    /**
     * Nothing OS applies additional dimming to frames submitted by a selected
     * Always-On Glyph Toy. setAppMatrixFrame does not use this path, which is why
     * the exact same effect is bright in the open app but dim after selecting it
     * in Glyph Toys. Compensate only the Toy output and preserve every luminance
     * level produced by MatrixEngine.
     */
    fun forGlyphToy(
        frame: IntArray,
        masterBrightness: Float,
        ambientScale: Float = 1f
    ): IntArray {
        val master = masterBrightness.coerceIn(0f, 1f).toDouble()
        if (master == 0.0) return IntArray(frame.size)
        return map(frame, master, ambientScale, outputGain = TOY_AOD_GAIN)
    }

    private fun map(
        frame: IntArray,
        master: Double,
        ambientScale: Float,
        outputGain: Double
    ): IntArray =
        IntArray(frame.size) { index ->
            val input = frame[index].coerceIn(0, PREVIEW_MAX.toInt())
            if (input == 0) {
                0
            } else {
                // MatrixEngine already applies the user's master brightness for
                // the on-screen preview. Recover the source luminance, shape the
                // midtones, then apply master once to the physical 12-bit range.
                // This keeps preview rendering 8-bit while allowing the Glyph
                // Matrix to actually reach its full output instead of stopping at
                // 255/4095 (~6.2% of the available LED level).
                val source = (input / PREVIEW_MAX / master).coerceIn(0.0, 1.0)
                val automatic = ambientScale.coerceIn(0f, 1f).toDouble()
                (MATRIX_MAX * source.pow(OUTPUT_GAMMA) * master * automatic * outputGain)
                    .roundToInt().coerceAtLeast(1)
            }
        }
}
