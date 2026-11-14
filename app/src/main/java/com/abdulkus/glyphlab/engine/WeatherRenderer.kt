package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import kotlin.math.*

/** Layered, deterministic weather. Fractional positions are coverage, not blurred bitmap scaling. */
object WeatherRenderer {
    private val cloudShape = listOf("....###..", "...#...#.", ".##.....#", "#.......#", ".#######.")
    private val moonShape = listOf("..###", ".###.", "###..", "###..", "###..", ".###.", "..###")
    private val starSites = listOf(2 to 3, 4 to 1, 9 to 2, 10 to 4, 11 to 7, 9 to 10, 7 to 11, 4 to 11, 2 to 9, 1 to 6, 8 to 0, 5 to 12)

    fun render(condition: WeatherCondition, seconds: Double, tiltX: Float = 0f, tiltY: Float = 0f): IntArray {
        val c = Canvas()
        val t = seconds.coerceAtLeast(0.0)
        val tx = tiltX.coerceIn(-1f, 1f).toDouble()
        val ty = tiltY.coerceIn(-1f, 1f).toDouble()
        val cloudX = 2 + sin(t * 0.5) * 0.65 + tx * 0.7
        val cloudY = 1.6 + sin(t * 0.4) * 0.12 + ty * 0.3
        when (condition) {
            WeatherCondition.SUNNY -> sun(c, 6.0, 6.0, t, tx, ty, true)
            WeatherCondition.CLEAR_NIGHT -> {
                stars(c, t)
                moon(c, 3.7 + sin(t * 0.6) * 0.35 + tx * 0.7, 2.6 + cos(t * 0.45) * 0.25 + ty * 0.7)
            }
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                if (condition == WeatherCondition.PARTLY_CLOUDY_DAY) sun(c, 5.0, 4.0, t, tx, ty, false)
                else { stars(c, t); moon(c, 3.0 + sin(t * 0.6) * 0.3 + tx * 0.65, 1.0 + ty * 0.6) }
                cloud(c, 2.7 + sin(t * 0.5) * 0.8 + tx * 0.65, 6.1 + ty * 0.35, 235)
            }
            WeatherCondition.CLOUDY -> {
                cloud(c, 1.7 - sin(t * 0.4) * 0.6 + tx * 0.35, 1.8 + ty * 0.25, 85)
                cloud(c, 2.8 + sin(t * 0.5) * 0.65 + tx * 0.7, 6.0 + ty * 0.4, 235)
            }
            WeatherCondition.FOG, WeatherCondition.RIME_FOG -> {
                for (row in 0..2) {
                    val y = 4.0 + row * 2 + ty * 0.3
                    val offset = sin(t * 0.65 + row * 2) * 0.8 + tx * 0.6
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
    private fun moon(c: Canvas, x: Double, y: Double) = c.stamp(x, y, moonShape, 245, opaque = true)
    private fun sun(c: Canvas, x: Double, y: Double, t: Double, tx: Double, ty: Double, large: Boolean) {
        val cx = x + sin(t * 0.55) * 0.28 + tx * 0.75
        val cy = y + sin(t * 0.43 + 0.7) * 0.23 + ty * 0.75
        val radius = if (large) 2.0 else 1.2
        val rayInner = if (large) 3.5 else 2.7
        val rayOuter = if (large) 4.3 else 3.2
        repeat(8) { i ->
            val a = i * PI / 4 + t * 0.12
            c.line(cx + cos(a) * rayInner, cy + sin(a) * rayInner, cx + cos(a) * rayOuter, cy + sin(a) * rayOuter, 230.0)
        }
        // Complete body on its own layer, including the lower arc behind the cloud.
        repeat(64) { i ->
            val a = i * PI / 32
            c.dot(cx + radius * cos(a), cy + radius * sin(a), 320.0)
        }
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
        val cycle = floor(t / 21).toLong()
        val phase = t % 21 - (5 + random(cycle, 181) * 9)
        if (phase in 0.0..1.6) {
            val a = random(cycle, 191) * 2 * PI
            val r = -10 + phase / 1.6 * 20
            val x = 6 + cos(a) * r; val y = 6 + sin(a) * r
            c.line(x - cos(a) * 2.2, y - sin(a) * 2.2, x, y, 135.0)
            c.dot(x, y, 245.0)
        }
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
        private val values = DoubleArray(169)
        fun dot(x: Double, y: Double, level: Double) {
            val ix = floor(x).toInt(); val iy = floor(y).toInt()
            for (dy in 0..1) for (dx in 0..1) {
                val px = ix + dx; val py = iy + dy
                if (MatrixEngine.isInsideMatrix(px, py)) {
                    val v = level * (1 - abs(x - px)) * (1 - abs(y - py))
                    values[py * 13 + px] = max(values[py * 13 + px], v)
                }
            }
        }
        fun line(x: Double, y: Double, ex: Double, ey: Double, level: Double) {
            val steps = max(1, (hypot(ex - x, ey - y) * 10).toInt())
            for (i in 0..steps) dot(x + (ex - x) * i / steps, y + (ey - y) * i / steps, level)
        }
        fun stamp(x: Double, y: Double, rows: List<String>, level: Int, opaque: Boolean = false) {
            val ink = DoubleArray(169); val coverage = DoubleArray(169)
            rows.forEachIndexed { dy, row ->
                val first = row.indexOf('#'); val last = row.lastIndexOf('#')
                row.forEachIndexed { dx, bit ->
                    if (bit != '#' && (!opaque || dx !in first..last)) return@forEachIndexed
                    val fx = x + dx; val fy = y + dy
                    val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
                    for (oy in 0..1) for (ox in 0..1) {
                        val px = ix + ox; val py = iy + oy
                        if (MatrixEngine.isInsideMatrix(px, py)) {
                            val w = (1 - abs(fx - px)) * (1 - abs(fy - py))
                            val index = py * 13 + px
                            coverage[index] += w
                            if (bit == '#') ink[index] += level * w
                        }
                    }
                }
            }
            values.indices.forEach { i -> values[i] = if (opaque) values[i] * (1 - coverage[i].coerceAtMost(1.0)) + ink[i] else max(values[i], ink[i]) }
        }
        fun frame() = IntArray(169) { i -> values[i].roundToInt().coerceIn(0, 255).let { if (it < 7) 0 else it } }
    }
}
