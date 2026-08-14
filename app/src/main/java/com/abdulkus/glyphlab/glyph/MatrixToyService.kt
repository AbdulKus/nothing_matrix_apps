package com.abdulkus.glyphlab.glyph

import android.app.KeyguardManager
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
import android.os.SystemClock
import com.abdulkus.glyphlab.data.AutoBrightnessSource
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.MinuteClockFrameCache
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MatrixToyService : Service(), SensorEventListener {
    private val engine = MatrixEngine()
    private val clockFrames = MinuteClockFrameCache()
    private val brightnessWake = Channel<Unit>(Channel.CONFLATED)
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var renderJob: Job? = null
    private var manager: GlyphMatrixManager? = null
    private lateinit var configStore: ConfigStore
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var screenBrightness: ScreenBrightnessMonitor
    private var motionSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var ambientBrightness = AutomaticBrightnessController()
    private var lightSensorRegistered = false
    private var screenReceiverRegistered = false
    private var config = MatrixConfig()
    private var tiltX = 0f
    private var tiltY = 0f
    private var motionOrientationKnown = false
    private var screenFacesDown = false
    private var lastReliableLux: Float? = null
    private var lastClockHardwareFrame: IntArray? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    rememberAmbientLux()
                    unregisterLightSensor()
                    // Swap the frame immediately instead of waiting for the
                    // next animation tick or the next minute-level AOD event.
                    renderSingleFrame()
                    ensureRenderLoop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    registerLightSensor()
                    renderSingleFrame()
                    ensureRenderLoop()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // The display can turn on while the keyguard is still
                    // visible. Blank lock-only clocks exactly when the user
                    // actually unlocks the phone.
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
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        screenBrightness = ScreenBrightnessMonitor(this) {
            brightnessWake.trySend(Unit)
        }
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun onBind(intent: Intent?): IBinder {
        config = configStore.load()
        val cachedLux = configStore.loadRecentAmbientLux()
        ambientBrightness = AutomaticBrightnessController(
            cachedLux?.let { AutomaticBrightnessController.targetScaleForLux(it) } ?: 1f
        )
        lastReliableLux = cachedLux
        motionOrientationKnown = false
        screenFacesDown = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        motionSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (powerManager.isInteractive) registerLightSensor()
        screenBrightness.start()
        if (!screenReceiverRegistered) {
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
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
        rememberAmbientLux()
        sensorManager.unregisterListener(this)
        lightSensorRegistered = false
        screenBrightness.stop()
        unregisterScreenReceiver()
        runCatching { manager?.turnOff() }
        runCatching { manager?.unInit() }
        manager = null
        return false
    }

    override fun onDestroy() {
        screenBrightness.stop()
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
                val outputMode = outputMode(current)
                val lowPowerOutput = outputMode != MatrixOutputMode.EFFECT
                if (!lowPowerOutput) lastClockHardwareFrame = null
                val frame = frameForCurrentState(current, outputMode)
                val automatic = automaticScale(current)
                val hardwareFrame = HardwareFrameMapper.forGlyphToy(
                    frame,
                    current.brightness,
                    automatic,
                    current.minimumBrightness
                )
                if (!lowPowerOutput || lastClockHardwareFrame?.contentEquals(hardwareFrame) != true) {
                    withContext(Dispatchers.Main.immediate) {
                        runCatching { manager?.setMatrixFrame(hardwareFrame) }
                    }
                    lastClockHardwareFrame = hardwareFrame
                }
                if (lowPowerOutput) {
                    val waitMillis = if (automaticBrightnessIsMoving(current)) {
                        CLOCK_BRIGHTNESS_STEP_MS
                    } else {
                        CLOCK_IDLE_POLL_MS
                    }
                    withTimeoutOrNull(waitMillis) { brightnessWake.receive() }
                } else {
                    delay(1000L / current.frameRate.coerceIn(8, 18))
                }
            }
        }
    }

    private fun renderSingleFrame() {
        config = configStore.load()
        val frame = frameForCurrentState(config, outputMode(config))
        runCatching {
            manager?.setMatrixFrame(
                HardwareFrameMapper.forGlyphToy(
                    frame,
                    config.brightness,
                    automaticScale(config),
                    config.minimumBrightness
                )
            )
        }
    }

    private fun automaticScale(current: MatrixConfig): Float {
        if (!current.autoBrightness) return 1f
        return when (current.autoBrightnessSource) {
            AutoBrightnessSource.AMBIENT_LIGHT ->
                ambientBrightness.advance(SystemClock.elapsedRealtimeNanos())
            AutoBrightnessSource.SCREEN_BRIGHTNESS -> screenBrightness.scale
        }
    }

    private fun automaticBrightnessIsMoving(current: MatrixConfig): Boolean {
        if (!current.autoBrightness) return false
        return when (current.autoBrightnessSource) {
            AutoBrightnessSource.AMBIENT_LIGHT -> ambientBrightness.isTransitioning
            AutoBrightnessSource.SCREEN_BRIGHTNESS -> screenBrightness.isTransitioning
        }
    }

    private fun frameForCurrentState(
        current: MatrixConfig,
        outputMode: MatrixOutputMode
    ): IntArray = when (outputMode) {
        MatrixOutputMode.CLOCK -> clockFrames.frame(masterBrightness = current.brightness)
        MatrixOutputMode.OFF -> OFF_FRAME
        MatrixOutputMode.EFFECT -> engine.render(current, System.nanoTime(), tiltX, tiltY)
    }

    private fun outputMode(current: MatrixConfig): MatrixOutputMode =
        resolveMatrixOutputMode(
            config = current,
            isInteractive = powerManager.isInteractive,
            isDeviceLocked = keyguardManager.isDeviceLocked
        )

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun rememberAmbientLux() {
        lastReliableLux?.let { configStore.saveAmbientLux(it) }
    }

    private fun registerLightSensor() {
        if (lightSensorRegistered) return
        lightSensor?.let {
            lightSensorRegistered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun unregisterLightSensor() {
        if (!lightSensorRegistered) return
        lightSensor?.let { sensorManager.unregisterListener(this, it) }
        lightSensorRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            // The front light sensor is covered when Flip to Glyph places the
            // phone screen-down. Keep the last measurement from before the flip
            // instead of mistaking the table for a dark room.
            val sensorIsUsable = motionSensor == null ||
                (motionOrientationKnown && !screenFacesDown)
            if (sensorIsUsable) {
                val lux = event.values[0].coerceAtLeast(0f)
                lastReliableLux = lux
                ambientBrightness.updateAmbientLux(lux, event.timestamp)
                brightnessWake.trySend(Unit)
            }
            return
        }
        if (event.sensor.type != motionSensor?.type) return
        motionOrientationKnown = true
        screenFacesDown = event.values[2] < -SensorManager.GRAVITY_EARTH * 0.25f
        val alpha = 0.13f
        val isGravityVector = event.sensor.type == Sensor.TYPE_GRAVITY
        val direction = if (isGravityVector) -1f else 1f
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH * direction).coerceIn(-1f, 1f)
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH * direction).coerceIn(-1f, 1f)
        tiltX += alpha * (x - tiltX)
        tiltY += alpha * (y - tiltY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val CLOCK_BRIGHTNESS_STEP_MS = 1000L / 24L
        const val CLOCK_IDLE_POLL_MS = 1_000L
        val OFF_FRAME = IntArray(MatrixEngine.PIXEL_COUNT)
    }
}
