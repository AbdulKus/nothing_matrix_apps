package com.abdulkus.glyphlab.widget

import android.content.Context
import android.graphics.*
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.WeatherRenderer
import com.abdulkus.glyphlab.weather.WeatherCondition
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders the circular Nothing OS weather widget dial:
 * - Transparent canvas background so Nothing OS dynamic theme / frosted glass applies seamlessly
 * - No artificial borders or rings
 * - 13x13 Nothing Glyph LED Matrix with authentic phosphor off-dots and crisp white lit LEDs
 * - Standard mode: expanded animation matrix (red dot removed) + monospace temperature and condition
 * - Full matrix mode: pure LED weather animation dots filling the entire circle without text
 */
object WeatherWidgetRenderer {

    fun renderDial(
        context: Context,
        condition: WeatherCondition,
        temperature: Double?,
        seconds: Double,
        matrixOnly: Boolean = false,
        size: Int = 320
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val unlitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 28, 28)
            style = Paint.Style.FILL
        }
        val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = size * 0.115f
            textAlign = Paint.Align.CENTER
        }
        val condPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 150, 154) // Nothing dim grey
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }

        val half = size / 2f
        val frame = WeatherRenderer.render(condition, seconds)

        if (matrixOnly) {
            // Full circle matrix mode: fill the entire circle with LED dots, no text or extras
            val matrixWidth = size * 0.86f
            val step = matrixWidth / 13f
            val startX = (size - matrixWidth) / 2f
            val startY = (size - matrixWidth) / 2f

            for (y in 0..12) {
                for (x in 0..12) {
                    if (MatrixEngine.isInsideMatrix(x, y)) {
                        val cx = startX + (x + 0.5f) * step
                        val cy = startY + (y + 0.5f) * step
                        val level = frame[y * 13 + x]

                        canvas.drawCircle(cx, cy, step * 0.30f, unlitPaint)

                        if (level > 0) {
                            val factor = (level / 255f).coerceIn(0f, 1f)
                            val rgb = (40 + factor * 215).roundToInt().coerceIn(0, 255)
                            litPaint.color = Color.rgb(rgb, rgb, rgb)
                            canvas.drawCircle(cx, cy, step * (0.30f + factor * 0.08f), litPaint)
                        }
                    }
                }
            }
        } else {
            // Standard mode: expanded matrix area (red dot removed), temperature & condition
            val matrixWidth = size * 0.63f
            val step = matrixWidth / 13f
            val startX = (size - matrixWidth) / 2f
            val startY = size * 0.075f

            for (y in 0..12) {
                for (x in 0..12) {
                    if (MatrixEngine.isInsideMatrix(x, y)) {
                        val cx = startX + (x + 0.5f) * step
                        val cy = startY + (y + 0.5f) * step
                        val level = frame[y * 13 + x]

                        canvas.drawCircle(cx, cy, step * 0.28f, unlitPaint)

                        if (level > 0) {
                            val factor = (level / 255f).coerceIn(0f, 1f)
                            val rgb = (40 + factor * 215).roundToInt().coerceIn(0, 255)
                            litPaint.color = Color.rgb(rgb, rgb, rgb)
                            canvas.drawCircle(cx, cy, step * (0.28f + factor * 0.08f), litPaint)
                        }
                    }
                }
            }

            // Temperature
            val tempText = if (temperature != null) {
                val rounded = temperature.roundToInt()
                "${if (rounded > 0) "+" else ""}$rounded°"
            } else {
                "--°"
            }
            canvas.drawText(tempText, half, size * 0.81f, tempPaint)

            // Condition label (respecting current locale)
            val isRu = Locale.getDefault().language.lowercase() == "ru"
            val rawCond = if (isRu) condition.ru else condition.en
            val condText = rawCond.uppercase()
            condPaint.textSize = if (condText.length > 12) size * 0.042f else size * 0.052f
            canvas.drawText(condText, half, size * 0.91f, condPaint)
        }

        return bitmap
    }
}
