package com.abdulkus.glyphlab.widget

import android.content.Context
import android.graphics.*
import com.abdulkus.glyphlab.data.WidgetTheme
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.WeatherRenderer
import com.abdulkus.glyphlab.weather.WeatherCondition
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders the circular Nothing OS 5 weather widget dial:
 * - Nothing OS 5 Frosted Glass support (System Dynamic Glass, Nothing Dark Glass, Transparent, AMOLED Black)
 * - Anti-aliased circular disc with subtle specular frosted glass highlight rim
 * - 13x13 Nothing Glyph LED Matrix with clearly visible phosphor off-dots and crisp white lit LEDs
 * - Standard mode: expanded animation matrix + monospace temperature and condition label
 * - Full matrix mode: pure LED weather animation dots filling the entire circle without text
 */
object WeatherWidgetRenderer {

    fun renderDial(
        context: Context,
        condition: WeatherCondition,
        temperature: Double?,
        seconds: Double,
        matrixOnly: Boolean = false,
        theme: WidgetTheme = WidgetTheme.SYSTEM,
        size: Int = 320
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        val discRadius = half - 3f

        // 1. Draw circular background disc without any artificial rim/outline
        when (theme) {
            WidgetTheme.SYSTEM -> {
                // Dynamically extract Nothing OS / Android Monet system wallpaper tint
                val tintColor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        val neutral2 = context.getColor(android.R.color.system_neutral2_800)
                        val wpColor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                            try {
                                android.app.WallpaperManager.getInstance(context)
                                    .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
                                    ?.primaryColor?.toArgb()
                            } catch (_: Throwable) { null }
                        } else null

                        if (wpColor != null) {
                            val wr = Color.red(wpColor)
                            val wg = Color.green(wpColor)
                            val wb = Color.blue(wpColor)
                            val nr = Color.red(neutral2)
                            val ng = Color.green(neutral2)
                            val nb = Color.blue(neutral2)
                            Color.rgb(
                                (nr * 0.60f + wr * 0.40f).roundToInt().coerceIn(0, 255),
                                (ng * 0.60f + wg * 0.40f).roundToInt().coerceIn(0, 255),
                                (nb * 0.60f + wb * 0.40f).roundToInt().coerceIn(0, 255)
                            )
                        } else {
                            neutral2
                        }
                    } catch (_: Throwable) {
                        Color.rgb(20, 20, 24)
                    }
                } else {
                    Color.rgb(20, 20, 24)
                }

                val r = Color.red(tintColor)
                val g = Color.green(tintColor)
                val b = Color.blue(tintColor)

                // Translucent disc matching stock Nothing widget density (~49% opacity)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(125, r, g, b)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.GLASS -> {
                // Classic Nothing dark translucent glass disc
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(125, 18, 18, 22)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.BLACK -> {
                // AMOLED Black
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(8, 8, 10)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.TRANSPARENT -> {
                // Transparent: no background disc, matrix dots float directly over wallpaper
            }
        }

        // 2. Phosphor off-dots: subtle, authentic dormant phosphor dots (not glaring)
        val unlitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(32, 255, 255, 255) // Subtle 12.5% opacity - clean and balanced
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
            color = Color.rgb(180, 180, 186) // Crisp Nothing secondary grey
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }

        val frame = WeatherRenderer.render(condition, seconds)

        if (matrixOnly) {
            // Full circle matrix mode: fill the entire circle with LED dots, no text or extras
            val matrixWidth = size * 0.86f
            val step = matrixWidth / 13f
            val startX = (size - matrixWidth) / 2f
            val startY = (size - matrixWidth) / 2f
            val baseRadius = step * 0.26f

            for (y in 0..12) {
                for (x in 0..12) {
                    if (MatrixEngine.isInsideMatrix(x, y)) {
                        val cx = startX + (x + 0.5f) * step
                        val cy = startY + (y + 0.5f) * step
                        val level = frame[y * 13 + x]

                        canvas.drawCircle(cx, cy, baseRadius, unlitPaint)

                        if (level > 0) {
                            val factor = (level / 255f).coerceIn(0f, 1f)
                            val alpha = (160 + factor * 95).roundToInt().coerceIn(0, 255)
                            litPaint.color = Color.argb(alpha, 255, 255, 255)
                            canvas.drawCircle(cx, cy, step * (0.26f + factor * 0.08f), litPaint)
                        }
                    }
                }
            }
        } else {
            // Standard mode: expanded matrix area, temperature & condition label
            val matrixWidth = size * 0.63f
            val step = matrixWidth / 13f
            val startX = (size - matrixWidth) / 2f
            val startY = size * 0.075f
            val baseRadius = step * 0.25f

            for (y in 0..12) {
                for (x in 0..12) {
                    if (MatrixEngine.isInsideMatrix(x, y)) {
                        val cx = startX + (x + 0.5f) * step
                        val cy = startY + (y + 0.5f) * step
                        val level = frame[y * 13 + x]

                        canvas.drawCircle(cx, cy, baseRadius, unlitPaint)

                        if (level > 0) {
                            val factor = (level / 255f).coerceIn(0f, 1f)
                            val alpha = (160 + factor * 95).roundToInt().coerceIn(0, 255)
                            litPaint.color = Color.argb(alpha, 255, 255, 255)
                            canvas.drawCircle(cx, cy, step * (0.25f + factor * 0.08f), litPaint)
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
            condPaint.textSize = when {
                condText.length > 18 -> size * 0.034f
                condText.length > 12 -> size * 0.040f
                else -> size * 0.050f
            }
            canvas.drawText(condText, half, size * 0.91f, condPaint)
        }

        return bitmap
    }
}
