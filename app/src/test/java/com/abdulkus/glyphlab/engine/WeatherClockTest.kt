package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.ClockFont
import com.abdulkus.glyphlab.data.ClockLayout
import com.abdulkus.glyphlab.weather.WeatherCondition
import com.abdulkus.glyphlab.weather.WeatherSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WeatherClockTest {
    @Test fun everyWmoConditionHasAnExplicitSceneAndPrecipitationWinsOverWind() {
        val supported = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        supported.forEach { code ->
            listOf(true, false).forEach { day -> assertNotEquals("WMO $code", WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(code, day)) }
        }
        assertEquals(WeatherCondition.CLEAR_NIGHT, WeatherCondition.fromWmo(0, false))
        assertEquals(WeatherCondition.PARTLY_CLOUDY_NIGHT, WeatherCondition.fromWmo(2, false))
        assertEquals(WeatherCondition.WIND, WeatherCondition.fromWmo(3, true, 40.0))
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherCondition.fromWmo(65, true, 70.0))
        assertEquals(WeatherCondition.SLEET, WeatherCondition.fromWmo(73, true, temperature = 1.0, rain = 0.2, snowfall = 0.1))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(123, true))
    }

    @Test fun oldAndFutureWeatherNeverMasqueradesAsLiveWeather() {
        val now = 1_800_000_000_000L
        assertEquals(WeatherCondition.SNOW, WeatherSnapshot(now, WeatherCondition.SNOW, -1.0).currentCondition(now))
        assertEquals(WeatherCondition.UNKNOWN, WeatherSnapshot(now - WeatherSnapshot.MAX_AGE_MS - 1, WeatherCondition.SNOW, -1.0).currentCondition(now))
        assertEquals(WeatherCondition.UNKNOWN, WeatherSnapshot(now + 3_600_000, WeatherCondition.SNOW, -1.0).currentCondition(now))
    }

    @Test fun weatherSilhouettesStayVisibleSparseAndAnimateWithoutLightingTheMaskCorners() {
        WeatherCondition.entries.forEach { condition ->
            val frames = (0..80).map { WeatherRenderer.render(condition, it / 10.0) }
            frames.forEach { frame ->
                assertEquals(169, frame.size)
                assertTrue("$condition needs a sparse silhouette", frame.count { it > 0 } in 7..65)
                frame.forEachIndexed { i, value ->
                    assertTrue(value in 0..255)
                    if (!MatrixEngine.isInsideMatrix(i % 13, i / 13)) assertEquals(0, value)
                }
            }
            if (condition != WeatherCondition.UNKNOWN) assertTrue("$condition must animate", frames.map { it.toList() }.distinct().size > 1)
        }
    }

    @Test fun fontsKeepEveryDigitDistinctAndNeverClipOnTheCircularDisplay() {
        ClockFont.entries.forEach { font ->
            val signatures = (0..9).map { digit ->
                val frame = ClockRenderer.renderTime(digit, digit, font, ClockLayout.STACKED)
                (1..5).flatMap { y -> (7 until 7 + font.width).map { x -> frame[y * 13 + x] } }
            }
            assertEquals("$font has ambiguous digits", 10, signatures.distinct().size)
            for (hour in 0..23) for (minute in 0..59) {
                val frame = ClockRenderer.renderTime(hour, minute, font, ClockLayout.STACKED)
                assertTrue(frame.count { it > 0 } in 20..80)
                assertTrue(frame.indices.filter { frame[it] > 0 }.all { MatrixEngine.isInsideMatrix(it % 13, it / 13) })
            }
        }
    }

    @Test fun defaultStylePreservesExistingClockAndStyleChangesInvalidateMinuteCache() {
        val time = 1_800_000_000_000L
        assertArrayEquals(SleepClockRenderer.render(time), ClockRenderer.render(time))
        val cache = MinuteClockFrameCache()
        val classic = cache.frame(time)
        val wide = cache.frame(time, font = ClockFont.WIDE)
        assertFalse(classic.contentEquals(wide))
        assertNotSame(wide, cache.frame(time, font = ClockFont.WIDE, layout = ClockLayout.HORIZON))
    }

    @Test fun exportActualRendererFramesForVisualReview() {
        fun frameJson(frame: IntArray) = frame.joinToString(",", "[", "]")
        val weather = WeatherCondition.entries.joinToString(",") { c ->
            "{\"name\":\"${c.ru}\",\"frames\":[" + (0 until 80).joinToString(",") { frameJson(WeatherRenderer.render(c, it / 10.0)) } + "]}"
        }
        val clocks = ClockFont.entries.joinToString(",") { font ->
            "{\"name\":\"${font.ru}\",\"frames\":[" + listOf(12 to 34, 20 to 58, 9 to 16).joinToString(",") { (h, m) -> frameJson(ClockRenderer.renderTime(h, m, font, ClockLayout.STACKED)) } + "]}"
        }
        val layouts = ClockLayout.entries.joinToString(",") { layout ->
            "{\"name\":\"${layout.ru}\",\"frames\":[${frameJson(ClockRenderer.renderTime(12, 34, ClockFont.WIDE, layout))}]}"
        }
        File("build/reports/matrix-review/frames.json").apply { parentFile?.mkdirs(); writeText("{\"weather\":[$weather],\"clocks\":[$clocks],\"layouts\":[$layouts]}") }
    }
}
