package com.abdulkus.glyphlab.weather

import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.engine.WeatherRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WeatherPreviewState(
    val condition: WeatherCondition? = null,
    val frame: IntArray = IntArray(MatrixEngine.PIXEL_COUNT)
)

/** Shared by the gallery, app output and AOD. A preview never changes cached live weather. */
object WeatherPreview {
    private val session = MutableStateFlow(WeatherPreviewState())
    val state = session.asStateFlow()
    private var lastNanos = 0L
    private var seconds = 0.0

    @Synchronized fun show(condition: WeatherCondition, nowNanos: Long = System.nanoTime()) {
        if (session.value.condition != condition) { lastNanos = nowNanos; seconds = 0.0 }
        session.update {
            if (it.condition == condition) it
            else WeatherPreviewState(condition, WeatherRenderer.render(condition, 0.0))
        }
    }
    @Synchronized fun clear() { session.value = WeatherPreviewState(); lastNanos = 0; seconds = 0.0 }
    @Synchronized fun timeSeconds(speed: Float, nowNanos: Long = System.nanoTime()): Double {
        if (session.value.condition == null) return 0.0
        val elapsed = ((nowNanos - lastNanos) / 1e9).coerceIn(0.0, 0.1)
        seconds += elapsed * speed * 2
        lastNanos = maxOf(lastNanos, nowNanos)
        return seconds
    }
    fun publish(condition: WeatherCondition, frame: IntArray) {
        // Drop an old render that completes after the user selects another scene or closes.
        session.update { if (it.condition == condition) it.copy(frame = frame) else it }
    }
}
