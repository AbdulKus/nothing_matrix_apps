package com.abdulkus.glyphlab.glyph

import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.abdulkus.glyphlab.data.AutoBrightnessSource
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.engine.ClockTransitionRenderer
import com.abdulkus.glyphlab.engine.TiltFilter
import com.abdulkus.glyphlab.engine.FramePacer
import com.abdulkus.glyphlab.weather.WeatherPreview
import com.abdulkus.glyphlab.weather.WeatherCondition
import com.abdulkus.glyphlab.weather.WeatherRepository
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.MinuteClockFrameCache
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class GlyphConnection { CONNECTING, CONNECTED, UNAVAILABLE }

class GlyphRuntime(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val configStore = ConfigStore(appContext)
    private val cachedAmbientLux = configStore.loadRecentAmbientLux()
    private var ambientBrightness = AutomaticBrightnessController(
        cachedAmbientLux?.let { AutomaticBrightnessController.targetScaleForLux(it) } ?: 1f
    )
    private val brightnessWake = Channel<Unit>(Channel.CONFLATED)
    private val screenBrightness = ScreenBrightnessMonitor(appContext) {
        brightnessWake.trySend(Unit)
    }
    private val weather = WeatherRepository.get(appContext)
    private val motionFilter = TiltFilter()
    private val engine = MatrixEngine()
    private val clockFrames = MinuteClockFrameCache()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _frame = MutableStateFlow(IntArray(MatrixEngine.PIXEL_COUNT))
    private val _connection = MutableStateFlow(GlyphConnection.CONNECTING)

    val frame: StateFlow<IntArray> = _frame.asStateFlow()
    val connection: StateFlow<GlyphConnection> = _connection.asStateFlow()

    @Volatile private var config = MatrixConfig()
    @Volatile private var outputEnabled = false
    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f
    @Volatile private var motionOrientationKnown = false
    @Volatile private var screenFacesDown = false
    @Volatile private var lastReliableLux: Float? = cachedAmbientLux
    private var manager: GlyphMatrixManager? = null
    private var loop: Job? = null
    @Volatile private var lastClockHardwareFrame: IntArray? = null

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            runCatching {
                manager?.register(Glyph.DEVICE_25111p)
                _connection.value = GlyphConnection.CONNECTED
            }.onFailure { _connection.value = GlyphConnection.UNAVAILABLE }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            _connection.value = GlyphConnection.UNAVAILABLE
        }
    }

    fun start(initialConfig: MatrixConfig) {
        motionFilter.reset()
        config = initialConfig
        if (motionSensor != null) {
            sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        screenBrightness.start()
        runCatching {
            manager = GlyphMatrixManager.getInstance(appContext)
            if (manager == null) {
                _connection.value = GlyphConnection.UNAVAILABLE
            } else {
                manager?.init(callback)
            }
        }.onFailure { _connection.value = GlyphConnection.UNAVAILABLE }

        if (loop == null) {
            loop = scope.launch {
                val pacer = FramePacer()
                var previewWasActive = false
                var previousClock = false
                var displayed = IntArray(169)
                val transition = ClockTransitionRenderer()
                while (isActive) {
                    val preview = WeatherPreview.state.value.condition
                    val current = if (preview == null) config else config.copy(effect = EffectType.WEATHER)
                    val clockVisible = current.effect == EffectType.CLOCK
                    if (!clockVisible) lastClockHardwareFrame = null
                    val nextRaw = if (clockVisible) {
                        clockFrames.frame(masterBrightness = current.brightness, font = current.clockFont, layout = current.clockLayout)
                    } else {
                        if (current.effect == EffectType.WEATHER && preview == null) weather.refreshIfNeeded()
                        engine.render(current, System.nanoTime(), tiltX, tiltY, preview ?: weather.state.value.snapshot?.currentCondition() ?: WeatherCondition.UNKNOWN,
                            weatherSecondsOverride = preview?.let { WeatherPreview.timeSeconds(current.speed) })
                    }
                    val now = System.nanoTime()
                    if (previousClock && !clockVisible) transition.start(displayed, current.clockTransition, now)
                    else if (clockVisible) transition.cancel()
                    val next = transition.frame(nextRaw, now)
                    previousClock = clockVisible
                    displayed = next
                    _frame.value = next
                    if (preview != null) WeatherPreview.publish(preview, next)
                    if (previewWasActive && preview == null && !outputEnabled) {
                        withContext(Dispatchers.Main.immediate) { runCatching { manager?.closeAppMatrix() } }
                    }
                    previewWasActive = preview != null
                    val automatic = automaticScale(current)
                    if ((outputEnabled || preview != null) && _connection.value == GlyphConnection.CONNECTED) {
                        val hardwareFrame = HardwareFrameMapper.forGlyph(
                            next,
                            current.brightness,
                            automatic,
                            current.minimumBrightness
                        )
                        if (!clockVisible || lastClockHardwareFrame?.contentEquals(hardwareFrame) != true) {
                            withContext(Dispatchers.Main.immediate) {
                                runCatching { manager?.setAppMatrixFrame(hardwareFrame) }
                                    .onFailure { _connection.value = GlyphConnection.UNAVAILABLE }
                            }
                            lastClockHardwareFrame = hardwareFrame
                        }
                    }
                    if (clockVisible) {
                        pacer.reset()
                        val waitMillis = if (automaticBrightnessIsMoving(current)) {
                            FramePacer.brightnessStepMillis(current.frameRate)
                        } else {
                            CLOCK_IDLE_POLL_MS
                        }
                        withTimeoutOrNull(waitMillis) { brightnessWake.receive() }
                    } else {
                        withTimeoutOrNull(pacer.delayMillis(current.frameRate)) { brightnessWake.receive() }
                    }
                }
            }
        }
    }

    fun updateConfig(newConfig: MatrixConfig) {
        config = newConfig
        brightnessWake.trySend(Unit)
    }

    fun setOutputEnabled(enabled: Boolean) {
        outputEnabled = enabled
        lastClockHardwareFrame = null
        if (!enabled) runCatching { manager?.closeAppMatrix() }
    }

    fun stop() {
        WeatherPreview.clear()
        outputEnabled = false
        lastReliableLux?.let { configStore.saveAmbientLux(it) }
        sensorManager.unregisterListener(this)
        screenBrightness.stop()
        runCatching { manager?.closeAppMatrix() }
        runCatching { manager?.unInit() }
        manager = null
        scope.cancel()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
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
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val motion = motionFilter.update(x, y, event.timestamp)
        val weatherVisible = config.effect == EffectType.WEATHER || WeatherPreview.state.value.condition != null
        tiltX = if (weatherVisible) x else motion.x
        tiltY = if (weatherVisible) y else motion.y
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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

    private companion object {
        const val CLOCK_IDLE_POLL_MS = 1_000L
    }
}
