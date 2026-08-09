package com.abdulkus.glyphlab.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MatrixToyService : Service(), SensorEventListener {
    private val engine = MatrixEngine()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var renderJob: Job? = null
    private var manager: GlyphMatrixManager? = null
    private lateinit var configStore: ConfigStore
    private lateinit var sensorManager: SensorManager
    private var config = MatrixConfig()
    private var tiltX = 0f
    private var tiltY = 0f

    private val messageHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    GlyphToy.EVENT_AOD -> renderSingleFrame()
                    GlyphToy.EVENT_CHANGE -> {
                        config = config.copy(
                            effect = enumValues<com.abdulkus.glyphlab.data.EffectType>()[
                                (config.effect.ordinal + 1) % enumValues<com.abdulkus.glyphlab.data.EffectType>().size
                            ]
                        )
                    }
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(messageHandler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            manager?.register(Glyph.DEVICE_25111p)
            startRenderLoop()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            renderJob?.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        configStore = ConfigStore(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onBind(intent: Intent?): IBinder {
        config = configStore.load()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        if (config.accelerometer) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        manager = GlyphMatrixManager.getInstance(applicationContext)
        manager?.init(callback)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        renderJob?.cancel()
        renderJob = null
        scope.cancel()
        sensorManager.unregisterListener(this)
        runCatching { manager?.turnOff() }
        runCatching { manager?.unInit() }
        manager = null
        return false
    }

    private fun startRenderLoop() {
        renderJob?.cancel()
        renderJob = scope.launch {
            while (isActive) {
                val frame = engine.render(config, System.nanoTime(), tiltX, tiltY)
                withContext(Dispatchers.Main.immediate) {
                    runCatching { manager?.setMatrixFrame(frame) }
                }
                delay(1000L / config.frameRate.coerceIn(8, 18))
            }
        }
    }

    private fun renderSingleFrame() {
        val frame = engine.render(config, System.nanoTime(), tiltX, tiltY)
        runCatching { manager?.setMatrixFrame(frame) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.13f
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        tiltX += alpha * (x - tiltX)
        tiltY += alpha * (y - tiltY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
