package com.abdulkus.glyphlab.widget

import android.content.Context
import android.graphics.*
import com.abdulkus.glyphlab.data.WidgetAccentColor
import com.abdulkus.glyphlab.data.WidgetTheme
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.WeatherRenderer
import com.abdulkus.glyphlab.weather.WeatherCondition
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders the circular Nothing OS 5 weather widget dial:
 * - Nothing OS 5 Frosted Glass & Material You background disc (Frosted/Solid wallpaper, Dark glass, Light glass, Pure White, AMOLED Black, Transparent)
 * - Anti-aliased circular disc with subtle specular frosted glass highlight rim
 * - 13x13 Nothing Glyph LED Matrix with authentic phosphor off-dots and customizable accent lit LEDs
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
        accent: WidgetAccentColor = WidgetAccentColor.WHITE,
        size: Int = 320,
        language: com.abdulkus.glyphlab.ui.AppLanguage? = null,
        stale: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        val discRadius = half - 3f

        // 1. Draw circular background disc
        fun extractWallpaperTint(dark: Boolean): Int {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                try {
                    val neutral = if (dark) {
                        context.getColor(android.R.color.system_neutral2_800)
                    } else {
                        context.getColor(android.R.color.system_neutral2_100)
                    }
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
                        val nr = Color.red(neutral)
                        val ng = Color.green(neutral)
                        val nb = Color.blue(neutral)
                        return Color.rgb(
                            (nr * 0.65f + wr * 0.35f).roundToInt().coerceIn(0, 255),
                            (ng * 0.65f + wg * 0.35f).roundToInt().coerceIn(0, 255),
                            (nb * 0.65f + wb * 0.35f).roundToInt().coerceIn(0, 255)
                        )
                    }
                    return neutral
                } catch (_: Throwable) {
                    return if (dark) Color.rgb(20, 20, 24) else Color.rgb(240, 240, 244)
                }
            }
            return if (dark) Color.rgb(20, 20, 24) else Color.rgb(240, 240, 244)
        }

        when (theme) {
            WidgetTheme.SYSTEM -> {
                // Frosted translucent wallpaper tint (Nothing OS default ~45% alpha)
                val tint = extractWallpaperTint(dark = true)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(115, Color.red(tint), Color.green(tint), Color.blue(tint))
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.WALLPAPER_SOLID -> {
                // 100% opaque wallpaper tint (solid Material You surface)
                val tint = extractWallpaperTint(dark = true)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(Color.red(tint), Color.green(tint), Color.blue(tint))
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.GLASS -> {
                // Nothing Dark Glass (~45% alpha)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(115, 18, 18, 22)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.SOLID_DARK -> {
                // Opaque dark surface
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(22, 22, 26)
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
            WidgetTheme.LIGHT_GLASS -> {
                // Light translucent frosted glass (~70% alpha)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(178, 242, 242, 246)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.LIGHT_WALLPAPER -> {
                // Light wallpaper tint (~85% alpha)
                val tint = extractWallpaperTint(dark = false)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(215, Color.red(tint), Color.green(tint), Color.blue(tint))
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.SOLID_LIGHT -> {
                // Opaque light grey surface
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(238, 238, 242)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.WHITE -> {
                // Pure White
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(255, 255, 255)
                }
                canvas.drawCircle(half, half, discRadius, bgPaint)
            }
            WidgetTheme.TRANSPARENT -> {
                // Transparent: no background disc
            }
        }

        // 2. Phosphor off-dots & Lit dots
        val isLightTheme = theme.isLight
        val unlitColor = if (isLightTheme) Color.argb(38, 0, 0, 0) else Color.argb(32, 255, 255, 255)
        val unlitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = unlitColor
            style = Paint.Style.FILL
        }

        val baseLitColor = if (isLightTheme) accent.hexLightBg else accent.hexDarkBg
        val litR = Color.red(baseLitColor)
        val litG = Color.green(baseLitColor)
        val litB = Color.blue(baseLitColor)

        val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val tempTextColor = if (isLightTheme) Color.rgb(20, 20, 20) else Color.WHITE
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tempTextColor
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = size * 0.115f
            textAlign = Paint.Align.CENTER
        }

        val condTextColor = if (isLightTheme) Color.rgb(90, 90, 96) else Color.rgb(180, 180, 186)
        val condPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = condTextColor
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }

        val frame = WeatherRenderer.render(condition, seconds)

        if (matrixOnly) {
            // Full circle matrix mode: fill the entire circle with LED dots
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
                            litPaint.color = Color.argb(alpha, litR, litG, litB)
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
                            litPaint.color = Color.argb(alpha, litR, litG, litB)
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
            val isRu = language?.let { it == com.abdulkus.glyphlab.ui.AppLanguage.RU }
                ?: (Locale.getDefault().language == "ru")
            val rawCond = if (isRu) condition.ru else condition.en
            val condText = if (stale) {
                if (isRu) "ПОСЛЕДНИЕ ДАННЫЕ" else "LAST READING"
            } else rawCond.uppercase()
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
