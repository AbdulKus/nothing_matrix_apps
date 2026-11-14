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

    fun show(condition: WeatherCondition) {
        session.update {
            if (it.condition == condition) it
            else WeatherPreviewState(condition, WeatherRenderer.render(condition, 0.0))
        }
    }
    fun clear() { session.value = WeatherPreviewState() }
    fun publish(condition: WeatherCondition, frame: IntArray) {
        // Drop an old render that completes after the user selects another scene or closes.
        session.update { if (it.condition == condition) it.copy(frame = frame) else it }
    }
}
