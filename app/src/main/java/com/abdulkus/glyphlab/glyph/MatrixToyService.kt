package com.abdulkus.glyphlab.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.SleepClockRenderer
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
    private lateinit var powerManager: PowerManager
    private var motionSensor: Sensor? = null
    private var screenReceiverRegistered = false
    private var config = MatrixConfig()
    private var tiltX = 0f
    private var tiltY = 0f

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON -> {
                    // Swap the frame immediately instead of waiting for the
                    // next animation tick or the next minute-level AOD event.
                    renderSingleFrame()
                    ensureRenderLoop()
                }
            }
        }
    }

    private val messageHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    GlyphToy.EVENT_AOD -> {
                        // Nothing wakes selected AOD toys with this event every
                        // minute. Always push an up-to-date frame here so clocks
                        // and time-based effects refresh without a wakelock.
                        renderSingleFrame()
                        ensureRenderLoop()
                    }
                    GlyphToy.EVENT_CHANGE -> {
                        config = config.copy(
                            effect = enumValues<com.abdulkus.glyphlab.data.EffectType>()[
                                (config.effect.ordinal + 1) % enumValues<com.abdulkus.glyphlab.data.EffectType>().size
                            ]
                        )
                        configStore.save(config)
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
            ensureRenderLoop()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            renderJob?.cancel()
            renderJob = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        configStore = ConfigStore(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onBind(intent: Intent?): IBinder {
        config = configStore.load()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        motionSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (!screenReceiverRegistered) {
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                Context.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
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
        unregisterScreenReceiver()
        runCatching { manager?.turnOff() }
        runCatching { manager?.unInit() }
        manager = null
        return false
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        super.onDestroy()
    }

    /**
     * Keep rendering smoothly only while Android naturally schedules this
     * process. No wakelock or foreground service is used, so once the CPU enters
     * sleep the loop simply stops receiving execution time. EVENT_AOD still
     * provides the system-supported minute refresh path.
     */
    private fun ensureRenderLoop() {
        if (renderJob?.isActive == true) return
        renderJob = scope.launch {
            while (isActive) {
                config = configStore.load()
                val current = config
                val frame = frameForCurrentState(current)
                val hardwareFrame = HardwareFrameMapper.forGlyphToy(frame, current.brightness)
                withContext(Dispatchers.Main.immediate) {
                    runCatching { manager?.setMatrixFrame(hardwareFrame) }
                }
                delay(1000L / current.frameRate.coerceIn(8, 18))
            }
        }
    }

    private fun renderSingleFrame() {
        config = configStore.load()
        val frame = frameForCurrentState(config)
        runCatching {
            manager?.setMatrixFrame(HardwareFrameMapper.forGlyphToy(frame, config.brightness))
        }
    }

    private fun frameForCurrentState(current: MatrixConfig): IntArray =
        if (current.sleepClockEnabled && !powerManager.isInteractive) {
            SleepClockRenderer.render()
        } else {
            engine.render(current, System.nanoTime(), tiltX, tiltY)
        }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != motionSensor?.type) return
        val alpha = 0.13f
        val isGravityVector = event.sensor.type == Sensor.TYPE_GRAVITY
        val direction = if (isGravityVector) -1f else 1f
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH * direction).coerceIn(-1f, 1f)
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH * direction).coerceIn(-1f, 1f)
        tiltX += alpha * (x - tiltX)
        tiltY += alpha * (y - tiltY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
