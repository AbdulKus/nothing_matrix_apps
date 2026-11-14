package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test

class WeatherRefinementTest {
    @Test fun cloudyMoonUsesTheSecondCustomerBitmapExactly() {
        val expected = listOf("..###", ".###.", "###..", "###..", "###..", "###..", ".###.", "..###")
        assertEquals(expected, WeatherRenderer.lunarShape(true))
        assertEquals(24, WeatherRenderer.lunarShape(true).sumOf { row -> row.count { it=='#' } })
        assertEquals(36, WeatherRenderer.lunarShape(false).sumOf { row -> row.count { it=='#' } })
        val frame = WeatherRenderer.render(WeatherCondition.PARTLY_CLOUDY_NIGHT,0.0)
        for(y in 2..5) for(x in 0..12) {
            val lit = x in 3..7 && expected[y-2][x-3]=='#'
            assertEquals(lit, frame[y*13+x]==245)
        }
    }

    @Test fun precipitationNeverLeaksAboveCloudOrBelowIceEvenWithTilt() {
        val rain = listOf(WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN, WeatherCondition.DRIZZLE, WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW)
        for(tx in listOf(-1f,0f,1f)) for(tick in 0..400) {
            val t=tick/10.0
            val top=WeatherRenderer.cloudPose(t,tx.toDouble()).second.toInt()
            rain.forEach { condition ->
                val frame=WeatherRenderer.render(condition,t,tx,1f)
                for(y in 0 until top) for(x in 0..12) assertEquals(0,frame[y*13+x])
            }
            listOf(WeatherCondition.FREEZING_DRIZZLE,WeatherCondition.FREEZING_RAIN).forEach { condition ->
                val frame=WeatherRenderer.render(condition,t,tx,1f)
                for(x in 0..12) {
                    assertEquals(0,frame[12*13+x])
                    assertEquals(if(x in 3..9) if(x%2==0)200 else 65 else 0, frame[11*13+x])
                }
            }
        }
    }

    @Test fun cloudHeightDoesNotFollowVerticalTiltAndTopMarginIsOneOrTwo() {
        repeat(300) { tick ->
            val t=tick/10.0
            assertTrue(WeatherRenderer.cloudPose(t,1.0).second in listOf(1.0,2.0))
            val a=WeatherRenderer.render(WeatherCondition.RAIN,t,0f,-1f)
            val b=WeatherRenderer.render(WeatherCondition.RAIN,t,0f,1f)
            for(i in 0 until 6*13) assertEquals(a[i],b[i])
        }
        assertEquals(2.0,WeatherRenderer.displacement(1.0),0.0)
        assertEquals(-2.0,WeatherRenderer.displacement(-1.0),0.0)
    }

    @Test fun sunMatchesReferenceAndOnlyMovesRaysOnePixelOutward() {
        val reference = listOf(0 to -4, 3 to -3, 4 to 0, 3 to 3, 0 to 4, -3 to 3, -4 to 0, -3 to -3)
        assertEquals(reference, WeatherRenderer.sunRayOffsets(0.0, true))
        val baseline = WeatherRenderer.render(WeatherCondition.SUNNY, 0.0)
        assertEquals(reference.map { (x, y) -> (y + 6) * 13 + x + 6 }.toSet(),
            baseline.indices.filter { baseline[it] == 230 }.toSet())
        val small = listOf(0 to -3, 2 to -2, 3 to 0, 2 to 2, 0 to 3, -2 to 2, -3 to 0, -2 to -2)
        val counts = mutableSetOf<Int>()
        repeat(9000) { tick ->
            val t = tick / 50.0
            assertEquals(small, WeatherRenderer.sunRayOffsets(t, false))
            val rays = WeatherRenderer.sunRayOffsets(t, true)
            assertEquals(8, rays.toSet().size)
            var moved = 0
            rays.zip(reference).forEach { (ray, base) ->
                if (ray != base) {
                    moved++
                    assertEquals(base.first + base.first.compareTo(0), ray.first)
                    assertEquals(base.second + base.second.compareTo(0), ray.second)
                }
            }
            assertTrue(moved in 0..2)
            counts += moved
        }
        assertEquals(setOf(0, 1, 2), counts)
    }

    @Test fun snowEmergesExactlyTwoRowsEarlierAsACompleteCross() {
        for (condition in listOf(WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW, WeatherCondition.SLEET, WeatherCondition.SNOW_SHOWERS)) {
            var found = false
            repeat(1000) { tick ->
                val t = tick / 25.0
                val frame = WeatherRenderer.render(condition, t)
                val cloudTop = WeatherRenderer.cloudPose(t, 0.0).second.toInt()
                // Previously the first possible snow centre was cloudTop + 6.
                for (x in 2..10) {
                    val y = cloudTop + 4
                    if (frame[y * 13 + x] == 250) {
                        found = true
                        listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                            assertTrue(frame[(y + dy) * 13 + x + dx] >= 170)
                        }
                    }
                }
            }
            assertTrue("$condition never emerges at the raised position", found)
        }
    }

    @Test fun windGustsTravelWithFadingTailsAtDifferentHeightsOneOrTwoAtATime() {
        val snapshots = (0..6000).map { WeatherRenderer.windGusts(it / 50.0) }
        val counts = snapshots.map { it.size }.toSet()
        assertTrue(1 in counts && 2 in counts)
        assertTrue(counts.all { it <= 2 })
        val tracks = snapshots.flatten().groupBy { it.id }.values
            .filter { it.first().age < 0.1 && it.last().age > 5.3 }
        assertTrue(tracks.map { it.first().y.toInt() }.toSet().size >= 2)
        tracks.forEach { track ->
            assertTrue(track.any { it.opacity in 0.1..0.8 })
            track.zipWithNext().forEach { (a, b) -> assertTrue(b.x > a.x) }
            assertTrue(track.last().opacity < 0.1)
        }
        val cloudPositions = (0..300).map { tick ->
            val frame = WeatherRenderer.render(WeatherCondition.WIND, tick / 20.0)
            frame.indices.filter { it / 13 <= 3 && frame[it] == 190 }.toSet()
        }
        assertTrue(cloudPositions.count { it.isNotEmpty() } > 100)
        val centres = cloudPositions.filter { it.isNotEmpty() }.map { sites -> sites.map { it % 13 }.average() }
        assertTrue(centres.max() - centres.min() > 6)
    }

    @Test fun heavyRainFillsMoreColumnsAndHasMoreDrops() {
        fun density(condition:WeatherCondition)= (0..500).sumOf { tick ->
            WeatherRenderer.render(condition,tick/10.0).drop(7*13).count { it>0 }
        }
        assertTrue(density(WeatherCondition.HEAVY_RAIN)>density(WeatherCondition.RAIN)*1.4)
        val columns=(0..500).flatMap { tick ->
            val frame=WeatherRenderer.render(WeatherCondition.HEAVY_RAIN,tick/10.0)
            frame.indices.filter { it/13>=7 && frame[it]>0 }.map { it%13 }
        }.toSet()
        assertTrue(columns.size>=9)
    }
}
