package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class WeatherMotionTest {
    private fun lower(frame: IntArray, firstRow: Int = 6) = frame.indices.filter { it / 13 >= firstRow && frame[it] > 0 }.toSet()

    private fun components(pixels: Set<Int>): List<Set<Int>> {
        val remaining = pixels.toMutableSet()
        val result = mutableListOf<Set<Int>>()
        while (remaining.isNotEmpty()) {
            val component = mutableSetOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(remaining.first())
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                if (!remaining.remove(p)) continue
                component += p
                listOf(p - 1, p + 1, p - 13, p + 13).filter { q ->
                    q in remaining && abs(q % 13 - p % 13) + abs(q / 13 - p / 13) == 1
                }.forEach { queue.add(it) }
            }
            result += component
        }
        return result
    }

    @Test fun snowContainsOnlyCompleteSnowflakesAndHeavySnowHasMoreOfThem() {
        val totals = listOf(WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW).map { condition ->
            (0 until 1200).sumOf { tick ->
                val frame = WeatherRenderer.render(condition, tick / 10.0)
                assertTrue("$condition snowflakes touch the cloud at $tick", (0..12).all { frame[6 * 13 + it] == 0 })
                val snow = lower(frame)
                if (condition == WeatherCondition.HEAVY_SNOW) assertTrue("Heavy snow lost its density at $tick", snow.size >= 10)
                components(snow).forEach { component ->
                    assertTrue("$condition contains a rain-like particle at $tick", component.size >= 5)
                    assertTrue("$condition lost its snowflake shape at $tick", component.any { p ->
                        listOf(p - 1, p + 1, p - 13, p + 13).all { it in component }
                    })
                }
                snow.size
            }
        }
        assertTrue("Heavy snowfall needs visibly more flakes", totals[1] > totals[0] * 1.3)
    }

    @Test fun sleetSnowflakesUseBothSidesAndTheCentreAndStayOnTheirFallTrajectory() {
        val xPositions = (0 until 1200).flatMap { tick -> WeatherRenderer.snowflakes(tick / 10.0, 1, 1.0).map { it.x } }.toSet()
        assertTrue(xPositions.any { it in 3..4 })
        assertTrue(xPositions.any { it in 5..7 })
        assertTrue(xPositions.any { it in 8..9 })
        assertTrue(xPositions.size >= 5)
        val early = WeatherRenderer.snowflakes(0.1, 1, 1.0).single()
        val later = WeatherRenderer.snowflakes(2.1, 1, 1.0).single()
        assertEquals(early.x, later.x)
        assertTrue(later.y > early.y)
    }

    @Test fun hailIsAlwaysTwoSeparateIntactTwoByTwoCubesWithoutLightning() {
        repeat(800) { tick ->
            val cubes = components(lower(WeatherRenderer.render(WeatherCondition.HAIL, tick / 10.0), firstRow = 7))
            assertEquals("Hail must not contain a bolt or rain at $tick", 2, cubes.size)
            cubes.forEach { cube ->
                assertEquals(4, cube.size)
                assertEquals(2, cube.map { it % 13 }.distinct().size)
                assertEquals(2, cube.map { it / 13 }.distinct().size)
            }
        }
    }

    @Test fun everyCloudSceneHasSmallHorizontalMotionIncludingPrecipitation() {
        val normal = listOf(WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE,
            WeatherCondition.RAIN, WeatherCondition.SHOWERS, WeatherCondition.HEAVY_RAIN,
            WeatherCondition.FREEZING_RAIN, WeatherCondition.SNOW_GRAINS,
            WeatherCondition.THUNDERSTORM, WeatherCondition.HEAVY_THUNDERSTORM,
            WeatherCondition.HAIL, WeatherCondition.THUNDERSTORM_HAIL)
        val snow = listOf(WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW, WeatherCondition.SNOW_SHOWERS, WeatherCondition.SLEET)
        val partly = listOf(WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT)
        (normal + snow + partly + WeatherCondition.CLOUDY).forEach { condition ->
            val row = when (condition) { in snow -> 1; in partly -> 6; else -> 2 }
            fun crest(time: Double) = (0..12).last { x -> WeatherRenderer.render(condition, time)[row * 13 + x] > 0 }
            val distance = crest(2.1) - crest(8.0)
            assertEquals("$condition cloud must move by two cells across its cycle", if (condition == WeatherCondition.CLOUDY) -2 else 2, distance)
        }
    }

    @Test fun lightningUsesDifferentCompleteShapesAndOriginsWithQuietPauses() {
        val strikes = (0..14).map { WeatherRenderer.strike(it * 6.0 + 0.2, false)!! }
        assertTrue(strikes.map { it.x }.distinct().size >= 4)
        assertEquals(3, strikes.map { it.variant }.distinct().size)
        repeat(200) { i ->
            val time = i * 6.0 + 0.2
            val pixels = WeatherRenderer.render(WeatherCondition.THUNDERSTORM, time)
            assertTrue(pixels.indices.count { it / 13 >= 7 && pixels[it] == 255 } >= 11)
            assertEquals(0, lower(WeatherRenderer.render(WeatherCondition.THUNDERSTORM, time + 2)).count { it / 13 >= 7 })
        }
        assertNull(WeatherRenderer.strike(3.0, false))
        assertNotNull(WeatherRenderer.strike(1.2, true))
    }
}
