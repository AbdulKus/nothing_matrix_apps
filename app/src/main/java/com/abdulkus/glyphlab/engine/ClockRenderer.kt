package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.ClockFont
import com.abdulkus.glyphlab.data.ClockLayout
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

object ClockRenderer {
    // Each alphabet is drawn for this resolution; wide glyphs are not scaled 3px glyphs.
    private val alphabets = mapOf(
        ClockFont.CLASSIC to listOf("111/101/101/101/111", "010/110/010/010/111", "111/001/111/100/111", "111/001/111/001/111", "101/101/111/001/001", "111/100/111/001/111", "111/100/111/101/111", "111/001/010/010/010", "111/101/111/101/111", "111/101/111/001/111"),
        ClockFont.SLIM to listOf("111/101/101/101/111", "010/010/010/010/010", "110/001/010/100/111", "110/001/010/001/110", "101/101/111/001/001", "111/100/110/001/110", "011/100/111/101/111", "111/001/001/010/010", "111/101/111/101/111", "111/101/111/001/110"),
        ClockFont.ROUND to listOf("010/101/101/101/010", "010/110/010/010/111", "110/001/010/100/111", "110/001/010/001/110", "101/101/111/001/001", "111/100/110/001/110", "011/100/110/101/010", "111/001/010/010/010", "010/101/010/101/010", "010/101/011/001/110"),
        ClockFont.WIDE to listOf("0110/1001/1001/1001/0110", "0010/0110/0010/0010/0111", "1110/0001/0110/1000/1111", "1110/0001/0110/0001/1110", "1001/1001/1111/0001/0001", "1111/1000/1110/0001/1110", "0111/1000/1110/1001/0110", "1111/0001/0010/0100/0100", "0110/1001/0110/1001/0110", "0110/1001/0111/0001/1110"),
        ClockFont.TECH to listOf("1111/1001/1001/1001/1111", "0010/0110/0010/0010/1111", "1111/0001/1111/1000/1111", "1111/0001/0111/0001/1111", "1001/1001/1111/0001/0001", "1111/1000/1111/0001/1111", "1111/1000/1111/1001/1111", "1111/0001/0010/0100/0100", "1111/1001/1111/1001/1111", "1111/1001/1111/0001/1111")
    ).mapValues { (_, digits) -> digits.map { it.split('/') } }

    fun render(wallClockMillis: Long = System.currentTimeMillis(), font: ClockFont = ClockFont.CLASSIC,
               layout: ClockLayout = ClockLayout.STACKED): IntArray {
        val time = Instant.ofEpochMilli(wallClockMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        return renderTime(time.hour, time.minute, font, layout)
    }

    fun renderTime(hour: Int, minute: Int, font: ClockFont, layout: ClockLayout): IntArray {
        val f = IntArray(MatrixEngine.PIXEL_COUNT)
        if (layout == ClockLayout.ANALOG) {
            repeat(12) { i ->
                val a = i * PI / 6 - PI / 2
                put(f, 6 + (5.7 * cos(a)).roundToInt(), 6 + (5.7 * sin(a)).roundToInt(), if (i % 3 == 0) 140 else 55)
            }
            hand(f, ((hour % 12) + minute / 60.0) * PI / 6, 3.1, 160)
            hand(f, minute * PI / 30, 4.8, 255)
            put(f, 6, 6, 255)
        } else {
            val x = (13 - (font.width * 2 + 1)) / 2
            pair(f, hour, x, 1, font, 255)
            pair(f, minute, x, 7, font, if (layout == ClockLayout.HORIZON) 185 else 255)
            if (layout == ClockLayout.HORIZON) for (dx in 4..8) put(f, dx, 6, 65)
        }
        return f
    }

    private fun pair(f: IntArray, number: Int, x: Int, y: Int, font: ClockFont, level: Int) {
        listOf(number / 10, number % 10).forEachIndexed { i, digit ->
            alphabets.getValue(font)[digit].forEachIndexed { dy, row ->
                row.forEachIndexed { dx, bit -> if (bit == '1') put(f, x + i * (font.width + 1) + dx, y + dy, level) }
            }
        }
    }
    private fun hand(f: IntArray, angle: Double, length: Double, level: Int) {
        for (step in 0..(length * 5).toInt()) {
            val r = step / 5.0
            put(f, 6 + (r * sin(angle)).roundToInt(), 6 - (r * cos(angle)).roundToInt(), level)
        }
    }
    private fun put(f: IntArray, x: Int, y: Int, level: Int) {
        if (MatrixEngine.isInsideMatrix(x, y)) f[y * 13 + x] = max(f[y * 13 + x], level)
    }
}
