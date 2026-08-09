package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.MatrixConfig
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Renders a tiny clock over any already-rendered 13x13 effect frame. */
object ClockOverlayRenderer {
    private const val GLYPH_WIDTH = 3
    private const val GLYPH_HEIGHT = 5

    private val digits = mapOf(
        '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
        '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
        '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
        '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
        '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
        '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
        '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
        '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111)
    )

    fun apply(
        frame: IntArray,
        config: MatrixConfig,
        wallClockMillis: Long = System.currentTimeMillis()
    ): IntArray {
        if (!config.clockEnabled || frame.size != MatrixEngine.PIXEL_COUNT) return frame

        val maxLevel = (255f * config.brightness.coerceIn(0f, 1f)).roundToInt()
        if (maxLevel <= 0) return IntArray(frame.size)

        val time = Instant.ofEpochMilli(wallClockMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val hours = "%02d".format(time.hour)
        val minutes = "%02d".format(time.minute)
        val virtual = if (config.clockTwoLines) {
            buildTwoLineMask(hours, minutes)
        } else {
            buildOneLineMask(hours, minutes)
        }

        val scale = if (config.clockTwoLines) {
            0.70f + config.clockScale.coerceIn(0f, 1f) * 0.45f
        } else {
            0.65f + config.clockScale.coerceIn(0f, 1f) * 0.35f
        }
        val glyphMask = rasterize(
            virtual = virtual,
            scale = scale,
            centerX = config.clockPositionX.coerceIn(0f, 1f) * (MatrixEngine.SIZE - 1),
            centerY = config.clockPositionY.coerceIn(0f, 1f) * (MatrixEngine.SIZE - 1)
        )

        val output = frame.copyOf()
        val outline = config.clockOutline.coerceIn(0f, 1f)
        if (outline > 0f) {
            val outlineMask = BooleanArray(output.size)
            glyphMask.indices.filter { glyphMask[it] }.forEach { index ->
                val x = index % MatrixEngine.SIZE
                val y = index / MatrixEngine.SIZE
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until MatrixEngine.SIZE || ny !in 0 until MatrixEngine.SIZE) continue
                        if (!MatrixEngine.isInsideMatrix(nx, ny)) continue
                        val neighbor = ny * MatrixEngine.SIZE + nx
                        if (!glyphMask[neighbor]) outlineMask[neighbor] = true
                    }
                }
            }
            outlineMask.indices.filter { outlineMask[it] }.forEach { index ->
                output[index] = (output[index] * (1f - outline)).roundToInt().coerceIn(0, 255)
            }
        }

        glyphMask.indices.filter { glyphMask[it] }.forEach { index ->
            output[index] = if (config.clockInvert) {
                (maxLevel - output[index].coerceIn(0, maxLevel)).coerceIn(0, maxLevel)
            } else {
                max(output[index], maxLevel)
            }
        }
        return output
    }

    private fun buildOneLineMask(hours: String, minutes: String): VirtualMask {
        // 3 + 3 + 1 + 3 + 3 = exactly 13 virtual columns. Digits touch by
        // design so a full-size HH:MM can still fit on the 13x13 matrix.
        val width = 13
        val height = GLYPH_HEIGHT
        val pixels = BooleanArray(width * height)
        drawDigit(pixels, width, hours[0], 0, 0)
        drawDigit(pixels, width, hours[1], 3, 0)
        drawColon(pixels, width, 6, 0)
        drawDigit(pixels, width, minutes[0], 7, 0)
        drawDigit(pixels, width, minutes[1], 10, 0)
        return VirtualMask(width, height, pixels)
    }

    private fun buildTwoLineMask(hours: String, minutes: String): VirtualMask {
        val width = 6
        val height = GLYPH_HEIGHT * 2 + 1
        val pixels = BooleanArray(width * height)
        drawDigit(pixels, width, hours[0], 0, 0)
        drawDigit(pixels, width, hours[1], 3, 0)
        drawDigit(pixels, width, minutes[0], 0, GLYPH_HEIGHT + 1)
        drawDigit(pixels, width, minutes[1], 3, GLYPH_HEIGHT + 1)
        return VirtualMask(width, height, pixels)
    }

    private fun drawDigit(
        pixels: BooleanArray,
        canvasWidth: Int,
        digit: Char,
        originX: Int,
        originY: Int
    ) {
        val rows = digits[digit] ?: return
        rows.forEachIndexed { y, row ->
            for (x in 0 until GLYPH_WIDTH) {
                if ((row and (1 shl (GLYPH_WIDTH - 1 - x))) != 0) {
                    val index = (originY + y) * canvasWidth + originX + x
                    if (index in pixels.indices) pixels[index] = true
                }
            }
        }
    }

    private fun drawColon(pixels: BooleanArray, canvasWidth: Int, x: Int, y: Int) {
        listOf(1, 3).forEach { dotY ->
            val index = (y + dotY) * canvasWidth + x
            if (index in pixels.indices) pixels[index] = true
        }
    }

    private fun rasterize(
        virtual: VirtualMask,
        scale: Float,
        centerX: Float,
        centerY: Float
    ): BooleanArray {
        val output = BooleanArray(MatrixEngine.PIXEL_COUNT)
        val scaledWidth = virtual.width * scale
        val scaledHeight = virtual.height * scale
        // centerX/centerY use LED indices (0..12), while raster math works in
        // cell-edge coordinates. +0.5 keeps a full-size 13-column clock exactly
        // aligned with columns 0..12 instead of shifting it by one LED.
        val originX = centerX + 0.5f - scaledWidth / 2f
        val originY = centerY + 0.5f - scaledHeight / 2f

        for (y in 0 until MatrixEngine.SIZE) {
            for (x in 0 until MatrixEngine.SIZE) {
                if (!MatrixEngine.isInsideMatrix(x, y)) continue
                val virtualX = floor(((x + 0.5f) - originX) / scale).toInt()
                val virtualY = floor(((y + 0.5f) - originY) / scale).toInt()
                if (virtualX !in 0 until virtual.width || virtualY !in 0 until virtual.height) continue
                if (virtual.pixels[virtualY * virtual.width + virtualX]) {
                    output[y * MatrixEngine.SIZE + x] = true
                }
            }
        }
        return output
    }

    private data class VirtualMask(
        val width: Int,
        val height: Int,
        val pixels: BooleanArray
    )
}
