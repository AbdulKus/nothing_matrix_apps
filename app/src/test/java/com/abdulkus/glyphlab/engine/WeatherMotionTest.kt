package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class WeatherMotionTest {
    @Test fun everyParticleKindUsesTheCentreAndBothSidesWithNewBirthPositions() {
        WeatherRenderer.Kind.entries.forEach { kind ->
            val births = (0..1200).flatMap { tick -> WeatherRenderer.samples(tick / 10.0, 0.9, 2.0, kind) }
                .distinctBy { it.id }
            assertTrue("$kind has no central particles", births.any { it.x in 5.0..7.0 })
            assertTrue(births.any { it.x < 4.0 })
            assertTrue(births.any { it.x > 8.0 })
            assertTrue(births.map { (it.x * 10).toInt() }.distinct().size > 25)
        }
    }

    @Test fun particlesStartBehindTheCloudAndLeaveOnlyAfterTheEntireShapeExits() {
        WeatherRenderer.Kind.entries.forEach { kind ->
            val tracks = (0..1200).flatMap { tick ->
                WeatherRenderer.samples(tick / 50.0, 1.2, 2.0, kind).map { it.id to it }
            }.groupBy({ it.first }, { it.second })
            val complete = tracks.values.filter { it.first().age < 0.04 && it.last().y > 15 }
            assertTrue("$kind needs full trajectories", complete.size >= 10)
            complete.forEach { track ->
                assertTrue(track.first().y in 4.6..4.7)
                assertTrue(track.last().y > 15)
                track.zipWithNext().forEach { (a, b) ->
                    assertTrue(b.y > a.y)
                    assertTrue(abs(b.x - a.x) < 0.05)
                }
            }
        }
    }

    @Test fun snowHasSmallAndLargeFlakesAndHeavySnowIsDenser() {
        val particles = WeatherRenderer.samples(20.0, 0.5, 1.6, WeatherRenderer.Kind.SNOW)
        assertTrue(particles.any { it.large }); assertTrue(particles.any { !it.large })
        fun density(condition: WeatherCondition) = (0..400).sumOf { tick ->
            WeatherRenderer.render(condition, tick / 10.0).drop(7 * 13).count { it >= 45 }
        }
        assertTrue(density(WeatherCondition.HEAVY_SNOW) > density(WeatherCondition.SNOW) * 1.2)
    }

    @Test fun lightningGrowsAlongThreeSegmentsWithTwoCornersThenFades() {
        val active = (0..12000).mapNotNull { tick -> WeatherRenderer.bolt(tick / 100.0)?.let { tick to it } }
        assertTrue(active.any { it.second.growth in 0.1..0.8 })
        assertTrue(active.any { it.second.growth == 1.0 })
        assertTrue(active.any { it.second.level in 10.0..180.0 })
        assertTrue(active.map { it.second.points }.distinct().size > 12)
        active.forEach { (_, bolt) ->
            assertEquals(4, bolt.points.size)
            assertTrue(bolt.points.zipWithNext().all { (a,b) -> b.second > a.second })
        }
        assertTrue((0..1200).count { WeatherRenderer.bolt(it / 10.0) == null } > 1000)
    }

    @Test fun celestialBodiesMoveOnBothAxesWhileStarsRemainInTheirOwnLayer() {
        listOf(WeatherCondition.SUNNY, WeatherCondition.CLEAR_NIGHT, WeatherCondition.PARTLY_CLOUDY_DAY).forEach { condition ->
            val base = WeatherRenderer.render(condition, 3.0)
            assertFalse(base.contentEquals(WeatherRenderer.render(condition, 3.0, 1f, 0f)))
            assertFalse(base.contentEquals(WeatherRenderer.render(condition, 3.0, 0f, 1f)))
            assertFalse(base.contentEquals(WeatherRenderer.render(condition, 8.0)))
        }
        val a = WeatherRenderer.render(WeatherCondition.CLEAR_NIGHT, 4.0, -1f, -1f)
        val b = WeatherRenderer.render(WeatherCondition.CLEAR_NIGHT, 4.0, 1f, 1f)
        a.indices.filter { it % 13 <= 1 || it % 13 >= 11 || (it / 13 == 0 || it / 13 == 12) }.forEach { assertEquals(a[it], b[it]) }
    }

    @Test fun shapesUseOnlyTheirOwnBrightnessWithoutFuzzyNeighbours() {
        val palettes = mapOf(
            WeatherCondition.CLOUDY to setOf(0, 85, 235),
            WeatherCondition.SUNNY to setOf(0, 230, 255),
            WeatherCondition.HAIL to setOf(0, 225, 235),
            WeatherCondition.SNOW to setOf(0, 170, 180, 225, 250)
        )
        palettes.forEach { (condition, levels) ->
            repeat(360) { tick ->
                assertTrue("$condition contains interpolated edge pixels", WeatherRenderer.render(condition, tick / 10.0, 0.7f, -0.4f).all { it in levels })
            }
        }
    }

    @Test fun shootingStarsAlwaysDescendDiagonallyAndExitWithTheirTail() {
        val stars = (0..10500).mapNotNull { WeatherRenderer.meteor(it / 20.0) }
        assertTrue(stars.any { it.dx > 0 }); assertTrue(stars.any { it.dx < 0 })
        assertTrue(stars.map { (it.startY * 10).toInt() }.distinct().size > 10)
        stars.forEach { star ->
            assertTrue(star.dy > 0)
            assertTrue(abs(star.dy / star.dx) in 0.57..1.12)
            assertTrue(star.startX <= -4 || star.startX >= 16)
            assertTrue(star.endX <= -4 || star.endX >= 16)
            val tailEndX = star.endX - star.dx * 2.8
            assertTrue(tailEndX < 0 || tailEndX > 12)
            assertTrue(star.endY > star.startY)
        }
    }
}
