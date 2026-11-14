package com.abdulkus.glyphlab.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.abdulkus.glyphlab.HomeActivity
import com.abdulkus.glyphlab.R
import com.abdulkus.glyphlab.weather.WeatherCondition
import com.abdulkus.glyphlab.weather.WeatherRepository
import kotlinx.coroutines.*

/**
 * AppWidgetProvider for the Nothing OS circular weather widget.
 * Supports:
 * - Live Glyph Matrix weather animation burst on phone unlock (ACTION_USER_PRESENT)
 * - Interactive live animation + weather sync when user taps the LED matrix
 * - Quick-launch into Glyph Lab settings when user taps the temperature / label area
 * - One-tap home screen pinning via Nothing Launcher
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateStatic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_ANIMATE_WIDGET -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        WeatherRepository.get(context).refreshIfNeeded()
                        runAnimationLoop(context, durationSeconds = 8.0)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        runAnimationLoop(context, durationSeconds = 6.0)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ANIMATE_WIDGET = "com.abdulkus.glyphlab.ACTION_ANIMATE_WIDGET"
        private const val RC_ANIMATE = 2001
        private const val RC_OPEN = 2002

        @Volatile private var animationJob: Job? = null

        fun triggerAnimation(context: Context, durationSeconds: Double = 8.0) {
            animationJob?.cancel()
            animationJob = CoroutineScope(Dispatchers.Default).launch {
                runAnimationLoop(context.applicationContext, durationSeconds)
            }
        }

        fun updateStatic(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            val repo = WeatherRepository.get(context)
            val snapshot = repo.state.value.snapshot
            val condition = snapshot?.currentCondition() ?: WeatherCondition.CLOUDY
            val temp = snapshot?.temperature

            val bitmap = WeatherWidgetRenderer.renderDial(
                context = context,
                condition = condition,
                temperature = temp,
                seconds = 0.0,
                size = 320
            )

            val views = RemoteViews(context.packageName, R.layout.widget_weather).apply {
                setImageViewBitmap(R.id.widget_dial_image, bitmap)
                setupClickIntents(context, this)
            }

            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private suspend fun runAnimationLoop(context: Context, durationSeconds: Double) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            val repo = WeatherRepository.get(context)
            val startTime = System.nanoTime()
            val frameDurationMs = 100L // 10 fps smooth animation
            val totalFrames = (durationSeconds * 10).toInt()

            for (frame in 0 until totalFrames) {
                val elapsed = (System.nanoTime() - startTime) / 1e9
                val snapshot = repo.state.value.snapshot
                val condition = snapshot?.currentCondition() ?: WeatherCondition.CLOUDY
                val temp = snapshot?.temperature

                val bitmap = WeatherWidgetRenderer.renderDial(
                    context = context,
                    condition = condition,
                    temperature = temp,
                    seconds = elapsed,
                    size = 320
                )

                val views = RemoteViews(context.packageName, R.layout.widget_weather).apply {
                    setImageViewBitmap(R.id.widget_dial_image, bitmap)
                    setupClickIntents(context, this)
                }

                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, views)
                }

                delay(frameDurationMs)
            }

            // Settle to static frame when animation finishes
            updateStatic(context)
        }

        private fun setupClickIntents(context: Context, views: RemoteViews) {
            // Upper area (matrix): interactive animation trigger and weather sync
            val animateIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_ANIMATE_WIDGET
            }
            val animatePendingIntent = PendingIntent.getBroadcast(
                context,
                RC_ANIMATE,
                animateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_tap_matrix, animatePendingIntent)

            // Lower area (temp / condition): launch Glyph Lab settings
            val openIntent = Intent(context, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                RC_OPEN,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_tap_open, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_dial_image, openPendingIntent)
        }

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = ComponentName(context, WeatherWidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(context, "Long-press the home screen to add Nothing Weather Widget", Toast.LENGTH_LONG).show()
            }
        }
    }
}
