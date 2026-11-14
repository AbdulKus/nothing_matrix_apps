package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

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

    @Test fun sunRaysAreEightPointsAndRotationReversesSlowly() {
        val rotation=(0..600).map { WeatherRenderer.sunRotation(it.toDouble()) }
        val deltas=rotation.zipWithNext { a,b -> b-a }
        assertTrue(deltas.any { it>0.001 }); assertTrue(deltas.any { it< -0.001 })
        assertTrue(deltas.all { abs(it)<0.12 })
        repeat(300) { tick ->
            val frame=WeatherRenderer.render(WeatherCondition.SUNNY,tick/10.0)
            assertTrue(frame.count { it==230 } in 7..8)
        }
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
