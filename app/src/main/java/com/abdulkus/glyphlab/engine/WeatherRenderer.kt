package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import kotlin.math.*

/** Hand-pixelled silhouettes on the real 137 LED mask. Time is in seconds. */
object WeatherRenderer {
    fun render(condition: WeatherCondition, seconds: Double): IntArray {
        val f = IntArray(MatrixEngine.PIXEL_COUNT)
        val t = seconds % 3600
        when (condition) {
            WeatherCondition.SUNNY -> sun(f, 6, 6, t, large = true)
            WeatherCondition.CLEAR_NIGHT -> {
                moon(f, 4, 3)
                star(f, 9, 4, t, 0.0); star(f, 4, 10, t, 1.7); star(f, 10, 8, t, 3.4)
            }
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                if (condition == WeatherCondition.PARTLY_CLOUDY_DAY) sun(f, 5, 4, t, large = false)
                else moon(f, 3, 1)
                cloud(f, 3 + drift(t), 6, 240)
            }
            WeatherCondition.CLOUDY -> {
                cloud(f, 2 - drift(t), 2, 90)
                cloud(f, 3 + drift(t), 6, 235)
            }
            WeatherCondition.FOG -> {
                // Three separated ribbons, never a filled noisy screen.
                for (i in 0..2) {
                    val y = 4 + i * 2
                    val offset = (sin(t * 0.7 + i * 2) * 1.4).roundToInt()
                    for (x in 2..10) if (x != 4 + i + offset) put(f, x, y, if (i == 1) 225 else 135)
                }
                for (x in 4..8) put(f, x, 2, 60)
            }
            WeatherCondition.DRIZZLE -> { precipitation(f, t, 2, 1.6, 145); cloud(f, 2, 2, 225) }
            WeatherCondition.RAIN -> { precipitation(f, t, 3, 2.8, 220); cloud(f, 2, 2, 230) }
            WeatherCondition.HEAVY_RAIN -> { precipitation(f, t, 4, 4.2, 245, slant = true); cloud(f, 2, 2, 200) }
            WeatherCondition.FREEZING_RAIN -> {
                precipitation(f, t, 3, 2.6, 205)
                for (x in 3..9) put(f, x, 11, if (x % 2 == 0) 230 else 85)
                cloud(f, 2, 2, 220)
            }
            WeatherCondition.SLEET -> {
                precipitation(f, t, 2, 2.8, 175)
                snow(f, t, 3, 0.85); cloud(f, 2, 2, 225)
            }
            WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW -> {
                snow(f, t, if (condition == WeatherCondition.SNOW) 5 else 9, 0.85)
                cloud(f, 2, 1, 200)
            }
            WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL -> {
                val phase = t % 5.5
                // A held bolt rather than full-screen flashes: readable and calm.
                val bolt = if (phase < 0.75) 255 else 105
                sprite(f, 5, 6, listOf("..##", ".##.", "###.", "..#.", ".#.."), bolt)
                if (condition == WeatherCondition.HAIL) {
                    val y = 7 + ((t * 2).toInt() % 4)
                    put(f, 3, y, 235); put(f, 9, 17 - y, 235)
                    put(f, 3, 11, 90); put(f, 9, 11, 90)
                }
                cloud(f, 2, 2, 190 + (40 * sin(t)).roundToInt())
            }
            WeatherCondition.WIND -> {
                sprite(f, 2, 3, listOf(".....##.", ".......#", "########", "........", "######..", "......#.", "...###.."), 210)
                val x = 2 + ((t * 2).toInt() % 8)
                put(f, x, 10, 170); put(f, x - 1, 10, 70)
            }
            WeatherCondition.UNKNOWN -> {
                sprite(f, 4, 4, listOf(".###.", "#...#", "...#.", "..#..", ".....", "..#.."), 150)
            }
        }
        return f
    }

    private fun drift(t: Double) = (sin(t * 0.55) * 0.8).roundToInt()

    private fun cloud(f: IntArray, x: Int, y: Int, level: Int) {
        // Dark interior separates overlapping clouds from their bright outline.
        val rows = listOf("....###..", "...#...#.", ".##.....#", "#.......#", ".#######.")
        rows.forEachIndexed { dy, row -> row.forEachIndexed { dx, c ->
            if (c == '#') put(f, x + dx, y + dy, level)
            else if (dx > row.indexOf('#') && dx < row.lastIndexOf('#')) set(f, x + dx, y + dy, 0)
        } }
    }

    private fun sun(f: IntArray, x: Int, y: Int, t: Double, large: Boolean) {
        if (large) {
            sprite(f, x - 2, y - 2, listOf(".###.", "#...#", "#...#", "#...#", ".###."), 255)
            for (i in 0 until 8) {
                val angle = i * PI / 4
                val level = (155 + 65 * sin(t * 1.3 - i * PI / 4)).roundToInt()
                put(f, x + (4 * cos(angle)).roundToInt(), y + (4 * sin(angle)).roundToInt(), level)
            }
        } else {
            sprite(f, x - 1, y - 1, listOf("###", "#.#", "###"), 255)
            for ((dx, dy) in listOf(0 to -3, -3 to 0, 3 to 0, -2 to -2, 2 to -2))
                put(f, x + dx, y + dy, (165 + 45 * sin(t)).roundToInt())
        }
    }

    private fun moon(f: IntArray, x: Int, y: Int) = sprite(f, x, y,
        listOf("..###", ".###.", "###..", "###..", "###..", ".###.", "..###"), 245)

    private fun star(f: IntArray, x: Int, y: Int, t: Double, phase: Double) =
        put(f, x, y, (70 + 130 * (sin(t * 1.1 + phase) + 1) / 2).roundToInt())

    private fun precipitation(f: IntArray, t: Double, count: Int, speed: Double, level: Int, slant: Boolean = false) {
        repeat(count) { i ->
            val y = 6 + ((t * speed + i * 1.7) % 6).toInt()
            val x = 3 + i * 2 - if (slant) (y - 6) / 3 else 0
            put(f, x, y, level)
            if (count > 2 && y > 7) put(f, x, y - 1, level / 3)
        }
    }

    private fun snow(f: IntArray, t: Double, count: Int, speed: Double) {
        repeat(count) { i ->
            val y = 6 + ((t * speed + i * 1.43) % 6).toInt()
            val x = 2 + ((i * 3 + (sin(t * 0.8 + i) + 1).toInt()) % 9)
            put(f, x, y, 190 + (i % 3) * 30)
            // A small cross-shaped flake distinguishes snow from isolated drizzle.
            if (i == 0 && y in 7..10) {
                put(f, x - 1, y, 125); put(f, x + 1, y, 125)
                put(f, x, y - 1, 125); put(f, x, y + 1, 125)
            }
        }
    }

    private fun sprite(f: IntArray, x: Int, y: Int, rows: List<String>, level: Int) {
        rows.forEachIndexed { dy, row -> row.forEachIndexed { dx, c -> if (c == '#') put(f, x + dx, y + dy, level) } }
    }
    private fun set(f: IntArray, x: Int, y: Int, level: Int) {
        if (MatrixEngine.isInsideMatrix(x, y)) f[y * MatrixEngine.SIZE + x] = level.coerceIn(0, 255)
    }
    private fun put(f: IntArray, x: Int, y: Int, level: Int) {
        if (MatrixEngine.isInsideMatrix(x, y)) set(f, x, y, max(f[y * MatrixEngine.SIZE + x], level))
    }
}
