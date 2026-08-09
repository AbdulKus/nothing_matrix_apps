package com.abdulkus.glyphlab.glyph

import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GlyphConnection { CONNECTING, CONNECTED, UNAVAILABLE }

class GlyphRuntime(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val engine = MatrixEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _frame = MutableStateFlow(IntArray(MatrixEngine.PIXEL_COUNT))
    private val _connection = MutableStateFlow(GlyphConnection.CONNECTING)

    val frame: StateFlow<IntArray> = _frame.asStateFlow()
    val connection: StateFlow<GlyphConnection> = _connection.asStateFlow()

    @Volatile private var config = MatrixConfig()
    @Volatile private var outputEnabled = false
    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f
    private var manager: GlyphMatrixManager? = null
    private var loop: Job? = null

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
        config = initialConfig
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
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
                while (isActive) {
                    val current = config
                    val next = engine.render(current, System.nanoTime(), tiltX, tiltY)
                    _frame.value = next
                    if (outputEnabled && _connection.value == GlyphConnection.CONNECTED) {
                        withContext(Dispatchers.Main.immediate) {
                            runCatching { manager?.setAppMatrixFrame(next) }
                                .onFailure { _connection.value = GlyphConnection.UNAVAILABLE }
                        }
                    }
                    delay((1000L / current.frameRate.coerceIn(8, 30)))
                }
            }
        }
    }

    fun updateConfig(newConfig: MatrixConfig) {
        config = newConfig
    }

    fun setOutputEnabled(enabled: Boolean) {
        outputEnabled = enabled
        if (!enabled) runCatching { manager?.closeAppMatrix() }
    }

    fun stop() {
        outputEnabled = false
        sensorManager.unregisterListener(this)
        runCatching { manager?.closeAppMatrix() }
        runCatching { manager?.unInit() }
        manager = null
        scope.cancel()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val alpha = 0.16f
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        tiltX += alpha * (x - tiltX)
        tiltY += alpha * (y - tiltY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
