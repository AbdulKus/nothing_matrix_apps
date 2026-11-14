package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.ClockTransition
import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test

class ClockTransitionTest {
    private val clock = ClockRenderer.render(1_800_000_000_000L)
    private val destination = WeatherRenderer.render(WeatherCondition.RAIN, 3.0)

    @Test fun transitionsKeepExactEndpointsAndStayVisibleInsideTheMask() {
        ClockTransitionRenderer.STYLES.forEach { style ->
            assertArrayEquals(clock, ClockTransitionRenderer.compose(clock, destination, style, 0.0))
            assertArrayEquals(destination, ClockTransitionRenderer.compose(clock, destination, style, 1.0))
            (0..60).forEach { tick ->
                val frame = ClockTransitionRenderer.compose(clock, destination, style, tick / 60.0)
                assertTrue(frame.any { it > 0 })
                frame.forEachIndexed { i,v ->
                    assertTrue(v in 0..255)
                    if (!MatrixEngine.isInsideMatrix(i % 13, i / 13)) assertEquals(0,v)
                }
            }
        }
        assertEquals(4, ClockTransitionRenderer.STYLES.map { ClockTransitionRenderer.compose(clock, destination, it, 0.5).toList() }.distinct().size)
    }

    @Test fun destinationKeepsMovingDuringTransitionAndCompletionStopsCompositing() {
        val transition = ClockTransitionRenderer()
        transition.start(clock, ClockTransition.REASSEMBLE, 1_000_000_000L)
        assertArrayEquals(clock, transition.frame(destination, 1_000_000_000L))
        val moving = WeatherRenderer.render(WeatherCondition.RAIN, 4.0)
        val a = transition.frame(destination, 1_700_000_000L)
        val b = transition.frame(moving, 1_700_000_000L)
        assertFalse(a.contentEquals(b))
        assertSame(moving, transition.frame(moving, 2_000_000_000L))
        assertFalse(transition.active)
        transition.start(clock, ClockTransition.NONE, 3_000_000_000L)
        assertSame(destination, transition.frame(destination, 3_000_000_000L))
    }

    @Test fun tiltSmoothingDependsOnElapsedTimeAndRejectsRestNoise() {
        val a = TiltFilter(); val b = TiltFilter()
        var aa = TiltFilter.Tilt(0f,0f); var bb = aa
        repeat(101) { aa = a.update(1f,-1f, 1_000_000_000L + it * 10_000_000L) }
        repeat(51) { bb = b.update(1f,-1f, 1_000_000_000L + it * 20_000_000L) }
        assertEquals(aa.x, bb.x, 0.003f)
        assertTrue(aa.x in 0.95f..1f)
        val quiet = TiltFilter()
        repeat(100) { assertEquals(0f, quiet.update(0.01f,-0.01f, 1_000_000_000L + it * 20_000_000L).x, 0f) }
    }
}
