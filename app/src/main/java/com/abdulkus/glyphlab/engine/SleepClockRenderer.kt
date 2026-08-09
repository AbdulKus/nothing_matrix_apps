package com.abdulkus.glyphlab.engine

import java.time.Instant
import java.time.ZoneId

/** Fixed, stock-like HH/MM clock for the 13x13 AOD matrix. */
object SleepClockRenderer {
    private const val FIRST_DIGIT_X = 3
    private const val SECOND_DIGIT_X = 7
    private const val HOURS_Y = 1
    private const val MINUTES_Y = 7

    private val digits = mapOf(
        '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
        '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
        '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
        '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
        '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
        '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
        '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
        '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111)
    )

    fun render(wallClockMillis: Long = System.currentTimeMillis()): IntArray {
        val output = IntArray(MatrixEngine.PIXEL_COUNT)
        val time = Instant.ofEpochMilli(wallClockMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        drawPair(output, "%02d".format(time.hour), HOURS_Y)
        drawPair(output, "%02d".format(time.minute), MINUTES_Y)
        return output
    }

    private fun drawPair(output: IntArray, value: String, y: Int) {
        drawDigit(output, value[0], FIRST_DIGIT_X, y)
        drawDigit(output, value[1], SECOND_DIGIT_X, y)
    }

    private fun drawDigit(output: IntArray, digit: Char, originX: Int, originY: Int) {
        val rows = digits[digit] ?: return
        rows.forEachIndexed { y, row ->
            for (x in 0 until 3) {
                if ((row and (1 shl (2 - x))) == 0) continue
                val px = originX + x
                val py = originY + y
                if (MatrixEngine.isInsideMatrix(px, py)) {
                    output[py * MatrixEngine.SIZE + px] = 255
                }
            }
        }
    }
}
