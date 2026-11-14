package com.abdulkus.glyphlab.widget

import android.content.Context
import android.graphics.*
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.WeatherRenderer
import com.abdulkus.glyphlab.weather.WeatherCondition
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders the circular Nothing OS weather widget dial featuring:
 * - Pure AMOLED black circular canvas with signature 1dp industrial ring
 * - The iconic Nothing red status accent dot
 * - 13x13 Nothing Glyph LED Matrix with authentic phosphor off-dots and crisp white lit LEDs
 * - High-contrast monospace temperature and condition typography matching Nothing OS aesthetics
 */
object WeatherWidgetRenderer {

    fun renderDial(
        context: Context,
        condition: WeatherCondition,
        temperature: Double?,
        seconds: Double,
        size: Int = 320
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 38, 38)
            style = Paint.Style.STROKE
            strokeWidth = size * 0.008f
        }
        val redDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 25, 32) // Signature Nothing Red #D71920
            style = Paint.Style.FILL
        }
        val unlitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 24, 24)
            style = Paint.Style.FILL
        }
        val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = size * 0.11f
            textAlign = Paint.Align.CENTER
        }
        val condPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(138, 138, 142) // Nothing dim grey #8A8A8E
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }

        val half = size / 2f
        val radius = half - borderPaint.strokeWidth

        // 1. Circular AMOLED background and Nothing border ring
        canvas.drawCircle(half, half, radius, bgPaint)
        canvas.drawCircle(half, half, radius, borderPaint)

        // 2. The signature Nothing Red Accent Dot at top
        canvas.drawCircle(half, size * 0.082f, size * 0.016f, redDotPaint)

        // 3. 13x13 Glyph LED Matrix
        val frame = WeatherRenderer.render(condition, seconds)
        val matrixWidth = size * 0.54f
        val step = matrixWidth / 13f
        val startX = (size - matrixWidth) / 2f
        val startY = size * 0.13f

        for (y in 0..12) {
            for (x in 0..12) {
                if (MatrixEngine.isInsideMatrix(x, y)) {
                    val cx = startX + (x + 0.5f) * step
                    val cy = startY + (y + 0.5f) * step
                    val level = frame[y * 13 + x]

                    // Subtle phosphor off-LED dot
                    canvas.drawCircle(cx, cy, step * 0.28f, unlitPaint)

                    // Lit LED
                    if (level > 0) {
                        val factor = (level / 255f).coerceIn(0f, 1f)
                        val rgb = (35 + factor * 220).roundToInt().coerceIn(0, 255)
                        litPaint.color = Color.rgb(rgb, rgb, rgb)
                        canvas.drawCircle(cx, cy, step * (0.28f + factor * 0.08f), litPaint)
                    }
                }
            }
        }

        // 4. Glanceable temperature
        val tempText = if (temperature != null) {
            val rounded = temperature.roundToInt()
            "${if (rounded > 0) "+" else ""}$rounded°"
        } else {
            "--°"
        }
        canvas.drawText(tempText, half, size * 0.79f, tempPaint)

        // 5. Condition label (respecting current locale)
        val isRu = Locale.getDefault().language.lowercase() == "ru"
        val rawCond = if (isRu) condition.ru else condition.en
        val condText = rawCond.uppercase()
        condPaint.textSize = if (condText.length > 12) size * 0.042f else size * 0.052f
        canvas.drawText(condText, half, size * 0.89f, condPaint)

        return bitmap
    }
}
