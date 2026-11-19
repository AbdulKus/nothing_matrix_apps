package com.abdulkus.glyphlab.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.widget.RemoteViews
import android.widget.Toast
import com.abdulkus.glyphlab.HomeActivity
import com.abdulkus.glyphlab.R
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.ui.AppLanguage
import com.abdulkus.glyphlab.weather.WeatherCondition
import com.abdulkus.glyphlab.weather.WeatherState
import com.abdulkus.glyphlab.weather.WeatherRepository
import kotlinx.coroutines.*

/**
 * AppWidgetProvider for the Nothing OS circular live weather widget (standard with temp).
 * Coordinates continuous live animation for both standard and full-matrix widgets.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherWidgetRefreshJob.schedule(context)
        startLiveAnimation(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startLiveAnimation(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        checkAndStopIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_ANIMATE_WIDGET -> {
                WeatherWidgetRefreshJob.schedule(context)
                startLiveAnimation(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                startLiveAnimation(context)
            }
        }
    }

    companion object {
        const val ACTION_ANIMATE_WIDGET = "com.abdulkus.glyphlab.ACTION_ANIMATE_WIDGET"
        private const val RC_ANIMATE = 2001
        private const val RC_OPEN = 2002

        @Volatile private var liveJob: Job? = null
        @Volatile private var isReceiverRegistered = false
        private val startTime = System.nanoTime()

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> stopLiveAnimation()
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startLiveAnimation(context)
                }
            }
        }

        fun checkAndStopIfNoWidgets(context: Context) {
            val app = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(app)
            val standardIds = appWidgetManager.getAppWidgetIds(ComponentName(app, WeatherWidgetProvider::class.java))
            val matrixIds = appWidgetManager.getAppWidgetIds(ComponentName(app, WeatherMatrixWidgetProvider::class.java))
            if (standardIds.isEmpty() && matrixIds.isEmpty()) {
                stopLiveAnimation()
                WeatherWidgetRefreshJob.cancel(app)
            }
        }

        @Synchronized
        fun startLiveAnimation(context: Context) {
            val app = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(app)
            val standardComp = ComponentName(app, WeatherWidgetProvider::class.java)
            val matrixComp = ComponentName(app, WeatherMatrixWidgetProvider::class.java)
            val standardIds = appWidgetManager.getAppWidgetIds(standardComp)
            val matrixIds = appWidgetManager.getAppWidgetIds(matrixComp)

            if (standardIds.isEmpty() && matrixIds.isEmpty()) return

            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                runCatching {
                    app.registerReceiver(screenReceiver, filter)
                    isReceiverRegistered = true
                }
            }

            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return

            if (liveJob?.isActive == true) return

            liveJob = CoroutineScope(Dispatchers.Default).launch {
                val repo = WeatherRepository.get(app)
                val configStore = ConfigStore(app)
                val frameDelayMs = 125L
                var counter = 0
                var config = configStore.load()
                var language = widgetLanguage(app)

                repo.refreshIfNeeded()
                while (isActive) {
                    val currStandardIds = appWidgetManager.getAppWidgetIds(standardComp)
                    val currMatrixIds = appWidgetManager.getAppWidgetIds(matrixComp)
                    if ((currStandardIds.isEmpty() && currMatrixIds.isEmpty()) || !pm.isInteractive) break
                    if (counter % 8 == 0) {
                        config = configStore.load()
                        language = widgetLanguage(app)
                    }

                    val elapsed = (System.nanoTime() - startTime) / 1e9
                    publish(app, appWidgetManager, currStandardIds, currMatrixIds, repo.state.value, config, language, elapsed)

                    if (++counter % (60 * 8) == 0) repo.refreshIfNeeded()
                    delay(frameDelayMs)
                }
            }
        }

        @Synchronized
        fun stopLiveAnimation() {
            liveJob?.cancel()
            liveJob = null
        }

        @Synchronized
        fun updateStatic(context: Context) {
            if (liveJob?.isActive == true) return
            val manager = AppWidgetManager.getInstance(context)
            val standardIds = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            val matrixIds = manager.getAppWidgetIds(ComponentName(context, WeatherMatrixWidgetProvider::class.java))
            if (standardIds.isEmpty() && matrixIds.isEmpty()) return
            publish(
                context, manager, standardIds, matrixIds, WeatherRepository.get(context).state.value,
                ConfigStore(context).load(), widgetLanguage(context), 0.8
            )
        }

        private fun widgetLanguage(context: Context) = AppLanguage.fromStorageCode(
            context.getSharedPreferences("glyphlab_ui", Context.MODE_PRIVATE).getString("language", null)
        )

        private fun publish(
            context: Context,
            manager: AppWidgetManager,
            standardIds: IntArray,
            matrixIds: IntArray,
            weather: WeatherState,
            config: MatrixConfig,
            language: AppLanguage?,
            elapsed: Double
        ) {
            val snapshot = weather.snapshot
            val condition = snapshot?.condition ?: WeatherCondition.UNKNOWN
            fun publishStyle(ids: IntArray, matrixOnly: Boolean) {
                if (ids.isEmpty()) return
                val layout = if (matrixOnly) R.layout.widget_weather_matrix else R.layout.widget_weather
                val bitmap = WeatherWidgetRenderer.renderDial(
                    context, condition, snapshot?.temperature, elapsed, matrixOnly,
                    config.widgetTheme, config.widgetAccent, language = language,
                    stale = snapshot?.isCurrent() == false
                )
                val views = RemoteViews(context.packageName, layout).apply {
                    setImageViewBitmap(R.id.widget_dial_image, bitmap)
                    if (matrixOnly) WeatherMatrixWidgetProvider.setupClickIntents(context, this)
                    else setupClickIntents(context, this)
                }
                manager.updateAppWidget(ids, views)
            }
            publishStyle(standardIds, false)
            publishStyle(matrixIds, true)
        }

        private fun setupClickIntents(context: Context, views: RemoteViews) {
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
