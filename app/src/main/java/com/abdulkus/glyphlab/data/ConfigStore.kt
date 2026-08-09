package com.abdulkus.glyphlab.data

import android.content.Context
import android.content.SharedPreferences

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): MatrixConfig = MatrixConfig(
        effect = prefs.enum(KEY_EFFECT, EffectType.WIREFRAME),
        solid = prefs.enum(KEY_SOLID, SolidType.CUBE),
        speed = prefs.getFloat(KEY_SPEED, 0.42f),
        accelerometer = prefs.getBoolean(KEY_ACCELEROMETER, true),
        sensorStrength = prefs.getFloat(KEY_SENSOR_STRENGTH, 0.75f),
        brightness = prefs.getFloat(KEY_BRIGHTNESS, 0.9f),
        intensity = prefs.getFloat(KEY_INTENSITY, 0.7f),
        trail = prefs.getFloat(KEY_TRAIL, 0.35f),
        particleCount = prefs.getInt(KEY_PARTICLES, 28),
        showVertices = prefs.getBoolean(KEY_VERTICES, true),
        frameRate = prefs.getInt(KEY_FRAME_RATE, 24)
    )

    fun save(config: MatrixConfig) {
        prefs.edit()
            .putString(KEY_EFFECT, config.effect.name)
            .putString(KEY_SOLID, config.solid.name)
            .putFloat(KEY_SPEED, config.speed)
            .putBoolean(KEY_ACCELEROMETER, config.accelerometer)
            .putFloat(KEY_SENSOR_STRENGTH, config.sensorStrength)
            .putFloat(KEY_BRIGHTNESS, config.brightness)
            .putFloat(KEY_INTENSITY, config.intensity)
            .putFloat(KEY_TRAIL, config.trail)
            .putInt(KEY_PARTICLES, config.particleCount)
            .putBoolean(KEY_VERTICES, config.showVertices)
            .putInt(KEY_FRAME_RATE, config.frameRate)
            .apply()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(
        key: String,
        fallback: T
    ): T = runCatching {
        enumValueOf<T>(getString(key, fallback.name) ?: fallback.name)
    }.getOrDefault(fallback)

    private companion object {
        const val FILE_NAME = "glyph_lab_config"
        const val KEY_EFFECT = "effect"
        const val KEY_SOLID = "solid"
        const val KEY_SPEED = "speed"
        const val KEY_ACCELEROMETER = "accelerometer"
        const val KEY_SENSOR_STRENGTH = "sensor_strength"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_INTENSITY = "intensity"
        const val KEY_TRAIL = "trail"
        const val KEY_PARTICLES = "particles"
        const val KEY_VERTICES = "vertices"
        const val KEY_FRAME_RATE = "frame_rate"
    }
}
