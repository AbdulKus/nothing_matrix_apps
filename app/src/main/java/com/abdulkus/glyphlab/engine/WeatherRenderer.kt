package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import kotlin.math.*

/** Layered weather with crisp integer-pixel rasterization. Brightness changes only for actual effects. */
object WeatherRenderer {
    private val cloudShape = listOf("....###..", "...#...#.", ".##.....#", "#.......#", ".#######.")
    // Pixel coverage of a round 8x8 disk minus a smaller disk offset up/right.
    // Trim the empty right margin: the visible crescent is 6x8, with unequal curved horns.
    private val moonShape = listOf("..#...", ".##...", "##....", "##....", "###...", "###...", ".####.", "..####")
    private val starSites = listOf(2 to 3, 4 to 1, 9 to 2, 10 to 4, 11 to 7, 9 to 10, 7 to 11, 4 to 11, 2 to 9, 1 to 6, 8 to 0, 5 to 12)

    fun render(condition: WeatherCondition, seconds: Double, tiltX: Float = 0f, tiltY: Float = 0f): IntArray {
        val c = Canvas()
        val t = seconds.coerceAtLeast(0.0)
        val tx = tiltX.coerceIn(-1f, 1f).toDouble()
        val ty = tiltY.coerceIn(-1f, 1f).toDouble()
        // Whole-object translation is independent of the animation's fractional phase.
        // Symmetric steps keep left/right response identical, including exactly half tilt.
        val objectX = when { tx >= 0.5 -> 1.0; tx <= -0.5 -> -1.0; else -> 0.0 }
        val objectY = when { ty >= 0.5 -> 1.0; ty <= -0.5 -> -1.0; else -> 0.0 }
        val cloudX = 2 + sin(t * 0.5) * 0.65 + objectX
        val cloudY = 1.6 + sin(t * 0.4) * 0.12 + objectY
        when (condition) {
            WeatherCondition.SUNNY -> sun(c, 6.0, 6.0, t, objectX, objectY, true)
            WeatherCondition.CLEAR_NIGHT -> {
                stars(c, t)
                moon(c, 3.0 + sin(t * 0.6) * 0.55 + objectX, 2.0 + cos(t * 0.45) * 0.55 + objectY)
            }
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                if (condition == WeatherCondition.PARTLY_CLOUDY_DAY) sun(c, 5.0, 4.0, t, objectX, objectY, false)
                else { stars(c, t); moon(c, 2.5 + sin(t * 0.6) * 0.55 + objectX, 1.0 + objectY) }
                cloud(c, 2.7 + sin(t * 0.5) * 0.8 + objectX, 6.1 + objectY, 235)
            }
            WeatherCondition.CLOUDY -> {
                cloud(c, 1.7 - sin(t * 0.4) * 0.6 + objectX, 1.8 + objectY, 85)
                cloud(c, 2.8 + sin(t * 0.5) * 0.65 + objectX, 6.0 + objectY, 235)
            }
            WeatherCondition.FOG, WeatherCondition.RIME_FOG -> {
                for (row in 0..2) {
                    val y = 4.0 + row * 2 + objectY
                    val offset = sin(t * 0.65 + row * 2) * 0.8 + objectX
                    for (x in 2..10) if (x != 4 + row) c.dot(x + offset, y, if (row == 1) 205.0 else 120.0)
                }
                if (condition == WeatherCondition.RIME_FOG) ice(c)
            }
            WeatherCondition.WIND -> wind(c, t, tx, ty)
            WeatherCondition.UNKNOWN -> c.stamp(4.0, 4.0, listOf(".###.", "#...#", "...#.", "..#..", ".....", "..#.."), 150)
            else -> {
                when (condition) {
                    WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE -> particles(c, t, 0.83, 3.3, Kind.DRIZZLE, tx, ty)
                    WeatherCondition.RAIN -> particles(c, t, 0.58, 4.5, Kind.RAIN, tx, ty)
                    WeatherCondition.SHOWERS -> particles(c, t, 0.58, 4.6, Kind.RAIN, tx, ty, showers = true)
                    WeatherCondition.HEAVY_RAIN -> particles(c, t, 0.4, 6.0, Kind.RAIN, tx - 0.35, ty)
                    WeatherCondition.FREEZING_RAIN -> particles(c, t, 0.65, 4.0, Kind.RAIN, tx, ty)
                    WeatherCondition.SLEET -> {
                        particles(c, t, 0.95, 4.2, Kind.RAIN, tx, ty)
                        particles(c, t, 2.5, 1.9, Kind.SNOW, tx, ty)
                    }
                    WeatherCondition.SNOW -> particles(c, t, 2.4, 1.6, Kind.SNOW, tx, ty)
                    WeatherCondition.HEAVY_SNOW -> particles(c, t, 1.35, 1.8, Kind.SNOW, tx, ty)
                    WeatherCondition.SNOW_SHOWERS -> particles(c, t, 1.6, 1.9, Kind.SNOW, tx, ty, showers = true)
                    WeatherCondition.SNOW_GRAINS -> particles(c, t, 1.0, 2.5, Kind.GRAIN, tx, ty)
                    WeatherCondition.HAIL, WeatherCondition.THUNDERSTORM_HAIL -> particles(c, t, 1.05, 3.6, Kind.HAIL, tx, ty)
                    WeatherCondition.HEAVY_THUNDERSTORM -> particles(c, t, 0.9, 5.0, Kind.RAIN, tx, ty)
                    else -> Unit
                }
                if (condition == WeatherCondition.FREEZING_DRIZZLE || condition == WeatherCondition.FREEZING_RAIN) ice(c)
                val storm = condition in setOf(WeatherCondition.THUNDERSTORM, WeatherCondition.HEAVY_THUNDERSTORM, WeatherCondition.THUNDERSTORM_HAIL)
                cloud(c, cloudX, cloudY, if (storm) 155 else 225)
                if (storm) lightning(c, t, condition == WeatherCondition.HEAVY_THUNDERSTORM, tx * 0.5, ty * 0.25)
            }
        }
        return c.frame()
    }

    internal enum class Kind { DRIZZLE, RAIN, SNOW, HAIL, GRAIN }
    internal data class Particle(val id: Long, val x: Double, val y: Double, val large: Boolean, val age: Double)

    /** Infinite emission stream. A birth changes only the next particle, never one mid-fall. */
    internal fun samples(t: Double, interval: Double, speed: Double, kind: Kind, tx: Double = 0.0): List<Particle> {
        val life = 15.0 / (speed * 0.8)
        val latest = floor(t / interval).toLong() + 1
        val earliest = latest - ceil(life / interval).toInt() - 2
        return (earliest..latest).mapNotNull { id ->
            val birth = id * interval + (random(id, 11) - 0.5) * interval * 0.45
            val age = t - birth
            val velocity = speed * (0.85 + random(id, 29) * 0.3)
            val y = 4.6 + age * velocity
            if (age < 0 || y > 15.5) null else {
                val origin = 3.0 + sin(birth * 0.5) * 0.65 + random(id, 47) * 6.3
                val sway = if (kind == Kind.SNOW) sin(age * 1.2 + random(id, 59) * 6) * 0.38 else 0.0
                val x = origin + sway + tx * (y - 4.6) * 0.12
                Particle(id, x, y, random(id, 71) > 0.42, age)
            }
        }
    }

    private fun particles(c: Canvas, t: Double, interval: Double, speed: Double, kind: Kind, tx: Double, ty: Double, showers: Boolean = false) {
        samples(t, interval, speed, kind, tx).forEach { p ->
            // Vary emission density at birth. Existing particles always finish their flight.
            if (showers && sin((t - p.age) * 0.7) < -0.3 && random(p.id, 89) < 0.65) return@forEach
            val x = p.x + tx * 0.25
            val y = p.y + ty * 0.2
            when (kind) {
                Kind.DRIZZLE -> c.dot(x, y, 185.0)
                Kind.RAIN -> {
                    c.line(x - tx * 0.16, y - 0.75, x, y, 225.0)
                }
                Kind.SNOW -> if (p.large) {
                    c.dot(x, y, 250.0)
                    for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) c.dot(x + dx, y + dy, 170.0)
                } else c.stamp(x, y, listOf(".#", "##"), 180)
                Kind.HAIL -> c.stamp(x, y, listOf("##", "##"), 235)
                Kind.GRAIN -> { c.dot(x, y, 210.0); c.dot(x + 0.6, y - 0.6, 120.0) }
            }
        }
    }

    internal data class Bolt(val points: List<Pair<Double, Double>>, val growth: Double, val level: Double)
    internal fun bolt(t: Double, heavy: Boolean = false): Bolt? {
        val period = if (heavy) 4.3 else 6.1
        val cycle = floor(t / period).toLong()
        val phase = t - cycle * period - random(cycle, 101) * 0.7
        if (phase < 0 || phase > 0.68) return null
        val x = 3.2 + random(cycle, 107) * 5.5
        val sign = if (random(cycle, 109) < 0.5) -1 else 1
        val points = listOf(x to 5.2,
            (x + sign * (1.5 + random(cycle, 113))).coerceIn(2.0, 10.0) to 7.4,
            (x - sign * (0.6 + random(cycle, 127))).coerceIn(2.0, 10.0) to 8.5,
            (x + sign * (0.2 + random(cycle, 131))).coerceIn(2.0, 10.0) to 12.4)
        val growth = (phase / 0.18).coerceIn(0.0, 1.0)
        val fade = if (phase < 0.35) 1.0 else ((0.68 - phase) / 0.33).coerceIn(0.0, 1.0)
        return Bolt(points, growth, 255 * fade)
    }

    private fun lightning(c: Canvas, t: Double, heavy: Boolean, tx: Double, ty: Double) {
        val b = bolt(t, heavy) ?: return
        val lengths = b.points.zipWithNext { a, z -> hypot(z.first - a.first, z.second - a.second) }
        var remaining = lengths.sum() * b.growth
        lengths.forEachIndexed { i, length ->
            if (remaining > 0) {
                val a = b.points[i]; val z = b.points[i + 1]; val u = (remaining / length).coerceAtMost(1.0)
                c.line(a.first + tx, a.second + ty, a.first + (z.first - a.first) * u + tx, a.second + (z.second - a.second) * u + ty, b.level * 1.3)
                remaining -= length
            }
        }
    }

    private fun cloud(c: Canvas, x: Double, y: Double, level: Int) = c.stamp(x, y, cloudShape, level, opaque = true)
    private fun moon(c: Canvas, x: Double, y: Double) {
        // Keep both horns inside the real circular mask, even at the extreme tilt poses.
        c.stamp(x.roundToInt().coerceIn(2, 5).toDouble(), y.roundToInt().coerceIn(1, 4).toDouble(), moonShape, 245, opaque = true)
    }
    private fun sun(c: Canvas, x: Double, y: Double, t: Double, tx: Double, ty: Double, large: Boolean) {
        val cx = x + sin(t * 0.55) * 0.65 + tx
        val cy = y + sin(t * 0.43 + 0.7) * 0.65 + ty
        val rayInner = if (large) 3.5 else 2.7
        val rayOuter = if (large) 4.3 else 3.2
        repeat(8) { i ->
            val a = i * PI / 4 + t * 0.12
            c.line(cx + cos(a) * rayInner, cy + sin(a) * rayInner, cx + cos(a) * rayOuter, cy + sin(a) * rayOuter, 230.0)
        }
        // Full, fixed pixel alphabet on its own layer, including the arc behind the cloud.
        val body = if (large) listOf(".###.", "#...#", "#...#", "#...#", ".###.")
            else listOf("###", "#.#", "###")
        val offset = if (large) 2.0 else 1.0
        c.stamp(cx - offset, cy - offset, body, 255)
    }

    private fun stars(c: Canvas, t: Double) {
        repeat(4) { i ->
            val period = 13.0 + i * 2.7
            val cycle = floor((t + i * 3.1) / period).toLong()
            val phase = (t + i * 3.1) % period
            val site = starSites[(random(cycle, i + 151) * starSites.size).toInt().coerceAtMost(starSites.lastIndex)]
            val envelope = min(phase / 1.8, (period - phase) / 1.8).coerceIn(0.0, 1.0)
            val light = (70 + 80 * (sin(t * 1.1 + i * 2.4) + 1) / 2) * envelope
            c.dot(site.first.toDouble(), site.second.toDouble(), light)
        }
        meteor(t)?.let { star ->
            c.line(star.x - star.dx * 2.8, star.y - star.dy * 2.8, star.x, star.y, 100.0)
            c.line(star.x - star.dx * 1.3, star.y - star.dy * 1.3, star.x, star.y, 170.0)
            c.dot(star.x, star.y, 245.0)
        }
    }

    internal data class Meteor(
        val x: Double, val y: Double, val dx: Double, val dy: Double,
        val progress: Double, val startX: Double, val startY: Double, val endX: Double, val endY: Double
    )

    internal fun meteor(t: Double): Meteor? {
        val cycle = floor(t / 21).toLong()
        val phase = t - cycle * 21 - (5 + random(cycle, 181) * 9)
        val duration = 1.6
        if (phase !in 0.0..duration) return null
        val rightward = cycle % 2 == 0L
        val slope = tan((30 + random(cycle, 191) * 18) * PI / 180)
        val middleY = 4 + random(cycle, 197) * 3
        val sx = if (rightward) -4.0 else 16.0
        val ex = if (rightward) 16.0 else -4.0
        val sy = middleY - 10 * slope
        val ey = middleY + 10 * slope
        val length = hypot(ex - sx, ey - sy)
        val progress = phase / duration
        return Meteor(sx + (ex - sx) * progress, sy + (ey - sy) * progress,
            (ex - sx) / length, (ey - sy) / length, progress, sx, sy, ex, ey)
    }

    private fun ice(c: Canvas) { for (x in 3..9) c.dot(x.toDouble(), 11.0, if (x % 2 == 0) 200.0 else 65.0) }

    private fun wind(c: Canvas, t: Double, tx: Double, ty: Double) {
        // Coherent moving gusts, not a static pictogram. Bright heads carry dim curved tails.
        repeat(3) { i ->
            val x = ((t * (2.5 + i * 0.3) + i * 6) % 22) - 5
            val y = 3.0 + i * 3 + sin(t * 0.7 + i) * 0.45 + ty * 0.3
            for (j in 0..10) {
                val back = j * 0.35
                c.dot(x - back + tx * 0.4, y + sin(back * 0.7) * 0.45, 200 - j * 12.0)
            }
        }
        val cycle = floor(t / 6.5).toLong()
        val phase = t % 6.5
        val x = -3 + phase * 2.9
        val y = 6.4 + sin(phase * 1.1 + random(cycle, 211) * 6) * 1.5 + ty * 0.4
        val angle = phase * 1.5
        repeat(4) { i ->
            val a = angle + i * PI / 2
            c.dot(x + cos(a) * 0.85, y + sin(a) * 0.85, if (i % 2 == 0) 230.0 else 145.0)
        }
    }

    internal fun random(id: Long, salt: Int): Double {
        var n = id * 73856093L xor salt.toLong() * 19349663L
        n = (n xor (n ushr 13)) * 1274126177L
        return ((n xor (n ushr 19)) and 0x7fffffffL) / 2147483648.0
    }

    private class Canvas {
        private val values = IntArray(169)
        private fun put(x: Int, y: Int, level: Int) {
            if (MatrixEngine.isInsideMatrix(x, y)) {
                val i = y * 13 + x
                values[i] = max(values[i], level.coerceIn(0, 255))
            }
        }
        fun dot(x: Double, y: Double, level: Double) = put(x.roundToInt(), y.roundToInt(), level.roundToInt())

        fun line(x: Double, y: Double, ex: Double, ey: Double, level: Double) {
            // Bresenham: a one-pixel line, with no dim neighbours around the edge.
            var px = x.roundToInt(); var py = y.roundToInt()
            val endX = ex.roundToInt(); val endY = ey.roundToInt()
            val dx = abs(endX - px); val dy = -abs(endY - py)
            val sx = if (px < endX) 1 else -1
            val sy = if (py < endY) 1 else -1
            var error = dx + dy
            while (true) {
                put(px, py, level.roundToInt())
                if (px == endX && py == endY) break
                val e = 2 * error
                if (e >= dy) { error += dy; px += sx }
                if (e <= dx) { error += dx; py += sy }
            }
        }

        fun stamp(x: Double, y: Double, rows: List<String>, level: Int, opaque: Boolean = false) {
            // Snap the origin once so a sprite cannot change thickness during movement.
            val originX = x.roundToInt(); val originY = y.roundToInt()
            rows.forEachIndexed { dy, row ->
                val first = row.indexOf('#'); val last = row.lastIndexOf('#')
                row.forEachIndexed { dx, bit ->
                    val px = originX + dx; val py = originY + dy
                    if (MatrixEngine.isInsideMatrix(px, py)) {
                        if (bit == '#') {
                            if (opaque) values[py * 13 + px] = level.coerceIn(0, 255)
                            else put(px, py, level)
                        } else if (opaque && dx in first..last) values[py * 13 + px] = 0
                    }
                }
            }
        }
        fun frame() = values
    }
}
