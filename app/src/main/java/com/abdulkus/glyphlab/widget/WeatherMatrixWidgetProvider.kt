package com.abdulkus.glyphlab.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.abdulkus.glyphlab.R

/**
 * Full-circle Nothing Glyph Matrix weather widget without text or labels.
 * Pure animated weather LED dots taking up the entire circular dial.
 */
class WeatherMatrixWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherWidgetRefreshJob.schedule(context)
        WeatherWidgetProvider.startLiveAnimation(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWidgetProvider.startLiveAnimation(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWidgetProvider.checkAndStopIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_ANIMATE_MATRIX_WIDGET -> {
                WeatherWidgetRefreshJob.schedule(context)
                WeatherWidgetProvider.startLiveAnimation(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                WeatherWidgetProvider.startLiveAnimation(context)
            }
        }
    }

    companion object {
        const val ACTION_ANIMATE_MATRIX_WIDGET = "com.abdulkus.glyphlab.ACTION_ANIMATE_MATRIX_WIDGET"
        private const val RC_ANIMATE_MATRIX = 3001

        fun setupClickIntents(context: Context, views: RemoteViews) {
            val animateIntent = Intent(context, WeatherMatrixWidgetProvider::class.java).apply {
                action = ACTION_ANIMATE_MATRIX_WIDGET
            }
            val animatePendingIntent = PendingIntent.getBroadcast(
                context,
                RC_ANIMATE_MATRIX,
                animateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_tap_matrix, animatePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_dial_image, animatePendingIntent)
        }

        fun pinWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = ComponentName(context, WeatherMatrixWidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(context, "Long-press the home screen to add Nothing Weather Matrix Widget", Toast.LENGTH_LONG).show()
            }
        }
    }
}
