package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import kotlin.math.*

/** Hand-pixelled silhouettes on the real 137 LED mask. Time is in seconds. */
object WeatherRenderer {
    fun render(condition: WeatherCondition, seconds: Double, tiltX: Float = 0f, tiltY: Float = 0f): IntArray {
        val f = IntArray(MatrixEngine.PIXEL_COUNT)
        // Bounded, monotonic animation phase, shared by app preview and hardware.
        val t = seconds.coerceAtLeast(0.0) % 3600
        val lean = (tiltX / (1f + tiltY.coerceIn(-1f, 1f) * 0.3f)).coerceIn(-1f, 1f)
        val cloudOffset = lean.roundToInt()
        val cloudX = (2 + drift(t) + cloudOffset).coerceIn(1, 3)
        when (condition) {
            WeatherCondition.SUNNY -> sun(f, 6, 6, t, large = true)
            WeatherCondition.CLEAR_NIGHT -> {
                moon(f, 4, 3)
                star(f, 9, 4, t, 0.0); star(f, 4, 10, t, 1.7); star(f, 10, 8, t, 3.4)
            }
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                if (condition == WeatherCondition.PARTLY_CLOUDY_DAY) sun(f, 5, 4, t, large = false)
                else moon(f, 3, 1)
                cloud(f, (3 + drift(t) + cloudOffset).coerceIn(1, 4), 6, 240)
            }
            WeatherCondition.CLOUDY -> {
                cloud(f, (2 - drift(t) + cloudOffset).coerceIn(1, 3), 2, 90)
                cloud(f, (3 + drift(t) + cloudOffset).coerceIn(1, 4), 6, 235)
            }
            WeatherCondition.FOG, WeatherCondition.RIME_FOG -> {
                for (i in 0..2) {
                    val y = 4 + i * 2
                    val offset = (sin(t * 0.7 + i * 2) * 1.4).roundToInt() + cloudOffset
                    for (x in 2..10) if (x != 4 + i + offset) put(f, x, y, if (i == 1) 225 else 135)
                }
                if (condition == WeatherCondition.RIME_FOG) ice(f)
                else for (x in 4..8) put(f, x, 2, 60)
            }
            WeatherCondition.DRIZZLE -> {
                precipitation(f, t, 2, 1.6, 145, tiltX = lean); cloud(f, cloudX, 2, 225)
            }
            WeatherCondition.FREEZING_DRIZZLE -> {
                precipitation(f, t, 2, 1.6, 145, tiltX = lean); ice(f); cloud(f, cloudX, 2, 225)
            }
            WeatherCondition.RAIN, WeatherCondition.SHOWERS -> {
                val count = if (condition == WeatherCondition.SHOWERS && t % 6 > 3.5) 1 else 3
                precipitation(f, t, count, 2.8, 220, tiltX = lean); cloud(f, cloudX, 2, 230)
            }
            WeatherCondition.HEAVY_RAIN -> {
                precipitation(f, t, 4, 4.2, 245, slant = true, tiltX = lean); cloud(f, cloudX, 2, 200)
            }
            WeatherCondition.FREEZING_RAIN -> {
                precipitation(f, t, 3, 2.6, 205, tiltX = lean); ice(f); cloud(f, cloudX, 2, 220)
            }
            WeatherCondition.SLEET -> {
                precipitation(f, t, 2, 2.8, 175, tiltX = lean)
                snow(f, t, 1, 1.0, lean); cloud(f, cloudX, 1, 225)
            }
            WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW, WeatherCondition.SNOW_SHOWERS -> {
                val count = when (condition) {
                    WeatherCondition.HEAVY_SNOW -> 3
                    WeatherCondition.SNOW_SHOWERS -> if (t % 7 < 4) 3 else 1
                    else -> 2
                }
                snow(f, t, count, 0.85, lean); cloud(f, cloudX, 1, 200)
            }
            WeatherCondition.SNOW_GRAINS -> {
                grains(f, t, lean); cloud(f, cloudX, 2, 215)
            }
            WeatherCondition.THUNDERSTORM, WeatherCondition.HEAVY_THUNDERSTORM -> {
                if (condition == WeatherCondition.HEAVY_THUNDERSTORM && strike(t, true) == null) precipitation(f, t, 2, 3.6, 115, tiltX = lean)
                lightning(f, t, condition == WeatherCondition.HEAVY_THUNDERSTORM, lean)
                cloud(f, cloudX, 2, if (condition == WeatherCondition.HEAVY_THUNDERSTORM) 145 else 165)
            }
            WeatherCondition.HAIL, WeatherCondition.THUNDERSTORM_HAIL -> {
                if (condition == WeatherCondition.THUNDERSTORM_HAIL && strike(t, false) != null) lightning(f, t, false, lean)
                else hail(f, t, lean)
                cloud(f, cloudX, 2, if (condition == WeatherCondition.HAIL) 220 else 165)
            }
            WeatherCondition.WIND -> windsock(f, t, lean)
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

    private fun precipitation(f: IntArray, t: Double, count: Int, speed: Double, level: Int, slant: Boolean = false, tiltX: Float = 0f) {
        val lanes = when (count) { 1 -> listOf(6); 2 -> listOf(3, 9); 3 -> listOf(3, 6, 9); else -> listOf(2, 5, 8, 10) }
        repeat(count) { i ->
            val y = 7 + ((t * speed + i * 1.7) % 5).toInt()
            val x = lanes[i] + drift(t) + ((y - 7) * tiltX * 0.45f).roundToInt() - (if (slant) (y - 7) / 3 else 0)
            put(f, x, y, level)
            if (count > 2 && y > 7) put(f, x, y - 1, level / 3)
        }
    }

    internal data class Flake(val x: Int, val y: Int)

    /** A seeded spawn per fall, not a new random position per frame. Stable at every FPS. */
    internal fun snowflakes(t: Double, count: Int, speed: Double): List<Flake> {
        val flakes = mutableListOf<Flake>()
        repeat(count) { i ->
            val travel = t * speed + i * 4.0 / count
            val fall = floor(travel / 4).toInt()
            val phase = travel % 4
            // Whole 3x3 flakes enter/leave at the cloud/floor without turning into dots.
            if (phase >= 3) return@repeat
            // Separate spawn lanes and staggered phases keep shapes apart without
            // making an already falling flake disappear when another one approaches.
            val x = when (count) {
                1 -> 3 + spawn(fall, i, 7)
                2 -> (if (i == 0) 3 else 8) + spawn(fall, i, 2)
                else -> when (i) { 0 -> 2 + spawn(fall, i, 2); 1 -> 6; else -> 9 + spawn(fall, i, 2) }
            }
            flakes += Flake(x, 8 + phase.toInt())
        }
        return flakes
    }

    private fun snow(f: IntArray, t: Double, count: Int, speed: Double, tiltX: Float) {
        snowflakes(t, count, speed).forEach { (spawnX, y) ->
            val x = (spawnX + tiltX.roundToInt()).coerceIn(2, 10)
            put(f, x, y, 250)
            put(f, x - 1, y, 185); put(f, x + 1, y, 185)
            put(f, x, y - 1, 185); put(f, x, y + 1, 185)
        }
    }

    private fun spawn(fall: Int, particle: Int, range: Int): Int {
        var hash = (fall + 1) * 73856093 xor (particle + 17) * 19349663
        hash = (hash xor (hash ushr 13)) * 1274126177
        return (hash xor (hash ushr 16)).and(Int.MAX_VALUE) % range
    }

    private fun ice(f: IntArray) {
        for (x in 3..9) put(f, x, 11, if (x % 2 == 0) 230 else 85)
    }

    private fun grains(f: IntArray, t: Double, tiltX: Float) {
        repeat(3) { i ->
            val progress = t * 1.5 + i * 1.6
            val y = 7 + (progress % 5).toInt()
            val x = 3 + spawn((progress / 5).toInt(), i, 7) + ((y - 7) * tiltX * 0.4f).roundToInt()
            put(f, x, y, 225); put(f, x + 1, y - 1, 150)
        }
    }

    private fun hail(f: IntArray, t: Double, tiltX: Float) {
        repeat(2) { i ->
            val travel = t * 2.2 + i * 2.0
            val fall = (travel / 4).toInt()
            val y = 7 + (travel % 4).toInt()
            val x = ((if (i == 0) 2 else 8) + spawn(fall, i, 2) + ((y - 7) * tiltX * 0.4f).roundToInt()).coerceIn(2, 9)
            sprite(f, x, y, listOf("##", "##"), 240)
        }
    }

    // Bold 5x6 zigzags: a wide shoulder, a dark notch and a tapered two-cell stem.
    private val bolts = listOf(
        listOf("..###", ".###.", "####.", "..##.", ".##..", ".#..."),
        listOf("###..", ".###.", ".####", ".##..", "..##.", "...#."),
        listOf("...##", "..##.", ".####", "..##.", ".##..", "..#..")
    )

    internal data class Strike(val x: Int, val variant: Int, val level: Int)

    internal fun strike(t: Double, heavy: Boolean): Strike? {
        val period = if (heavy) 4.5 else 6.0
        val cycle = floor(t / period).toInt()
        val phase = t % period
        // Hold a complete silhouette; no whole-screen flashes or faint permanent bolt.
        val level = when {
            phase < 0.75 -> 255
            heavy && phase in 1.05..1.65 -> 235
            else -> return null
        }
        val x = listOf(2, 5, 3, 6, 4)[cycle % 5]
        return Strike((x + drift(cycle * period)).coerceIn(2, 7), cycle % bolts.size, level)
    }

    private fun lightning(f: IntArray, t: Double, heavy: Boolean, tiltX: Float) {
        strike(t, heavy)?.let { sprite(f, (it.x + tiltX.roundToInt()).coerceIn(2, 7), 6, bolts[it.variant], it.level) }
    }

    private fun windsock(f: IntArray, t: Double, tiltX: Float) {
        // A recognisable wind indicator: fixed mast, striped fabric and a waving tapered tip.
        for (y in 2..11) put(f, 3, y, 120)
        for (x in 2..4) put(f, x, 11, 165)
        for (dx in 0..6) {
            val sway = if (dx < 2) 0 else (sin(t * 2.0 - dx * 0.5) * dx / 5 + tiltX * dx / 7).roundToInt()
            val top = (if (dx < 4) 3 else 4) + sway
            val bottom = (if (dx < 4) 6 else 5) + sway
            val level = if (dx % 3 == 0) 250 else 175
            put(f, 4 + dx, top, level); put(f, 4 + dx, bottom, level)
            if (dx % 3 == 0) for (y in top..bottom) put(f, 4 + dx, y, level)
        }
        val gust = (t * 3 % 10).toInt()
        for (dx in 0..2) put(f, 3 + gust - dx, 9, 200 - dx * 55)
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
