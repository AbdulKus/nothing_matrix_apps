package com.abdulkus.glyphlab.glyph

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.abs

/** Observes the public system screen-brightness setting without polling frames. */
class ScreenBrightnessMonitor(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private val initialBrightness = readBrightness()
    private val controller = AutomaticBrightnessController(
        AutomaticBrightnessController.targetScaleForScreenBrightness(initialBrightness)
    )
    private var started = false
    private var targetBrightness = initialBrightness

    val scale: Float
        get() = controller.scale

    private val ramp = object : Runnable {
        override fun run() {
            controller.updateScreenBrightness(
                targetBrightness,
                SystemClock.elapsedRealtimeNanos()
            )
            val target = AutomaticBrightnessController.targetScaleForScreenBrightness(
                targetBrightness
            )
            if (started && abs(controller.scale - target) > 0.005f) {
                handler.postDelayed(this, RAMP_STEP_MS)
            }
        }
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            targetBrightness = readBrightness()
            handler.removeCallbacks(ramp)
            handler.post(ramp)
        }
    }

    fun start() {
        if (started) return
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer
        )
        started = true
        targetBrightness = readBrightness()
        handler.post(ramp)
    }

    fun stop() {
        if (!started) return
        handler.removeCallbacks(ramp)
        runCatching { resolver.unregisterContentObserver(observer) }
        started = false
    }

    private fun readBrightness(): Int = Settings.System.getInt(
        resolver,
        Settings.System.SCREEN_BRIGHTNESS,
        255
    )

    private companion object {
        const val RAMP_STEP_MS = 100L
    }
}
