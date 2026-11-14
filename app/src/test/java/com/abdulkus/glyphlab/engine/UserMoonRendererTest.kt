package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.weather.WeatherCondition
import org.junit.Assert.*
import org.junit.Test

class UserMoonRendererTest {
    // Exact customer drawing submitted through the 13x13 editor, not a renderer-derived snapshot.
    private val approved = listOf(
        "0000000000000",
        "0000000000000",
        "0000011110000",
        "0000111100000",
        "0001111000000",
        "0001111000000",
        "0001111000000",
        "0001111000000",
        "0001111000000",
        "0000111100000",
        "0000011110000",
        "0000000000000",
        "0000000000000"
    )

    @Test fun approvedDrawingMatchesPixelForPixelAtItsOriginalPosition() {
        val frame = WeatherRenderer.render(WeatherCondition.CLEAR_NIGHT, 0.0)
        val expected = approved.joinToString("").map { it.digitToInt() }.toIntArray()
        // Background stars have their own lower brightness; only the moon is 245 here.
        assertArrayEquals(expected, frame.map { if (it == 245) 1 else 0 }.toIntArray())
    }

    @Test fun motionOnlyTranslatesTheApprovedDrawingAndNeverClipsOrReshapesIt() {
        val expected = approved.flatMapIndexed { y, row ->
            row.mapIndexedNotNull { x, value -> if (value == '1') (x - 3) to (y - 2) else null }
        }.toSet()
        listOf(-1f, 0f, 1f).forEach { x -> listOf(-1f, 0f, 1f).forEach { y ->
            // No shooting star is emitted before t=5; this isolates the actual lunar layer.
            repeat(50) { tick ->
                val frame = WeatherRenderer.render(WeatherCondition.CLEAR_NIGHT, tick / 10.0, x, y)
                val pixels = frame.indices.filter { frame[it] == 245 }.map { it % 13 to it / 13 }
                assertEquals(36, pixels.size)
                val left = pixels.minOf { it.first }; val top = pixels.minOf { it.second }
                assertEquals(expected, pixels.map { (it.first - left) to (it.second - top) }.toSet())
            }
        } }
    }
}
