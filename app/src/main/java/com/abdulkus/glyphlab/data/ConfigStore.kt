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
        autoRotateX = prefs.getBoolean(KEY_AUTO_ROTATE_X, true),
        autoRotateY = prefs.getBoolean(KEY_AUTO_ROTATE_Y, true),
        autoRotateZ = prefs.getBoolean(KEY_AUTO_ROTATE_Z, false),
        accelerometer = prefs.getBoolean(KEY_ACCELEROMETER, true),
        sensorStrength = prefs.getFloat(KEY_SENSOR_STRENGTH, 0.75f),
        brightness = prefs.getFloat(KEY_BRIGHTNESS, 0.9f),
        intensity = prefs.getFloat(KEY_INTENSITY, 0.7f),
        trail = prefs.getFloat(KEY_TRAIL, 0.35f),
        particleCount = prefs.getInt(KEY_PARTICLES, 28),
        showVertices = prefs.getBoolean(KEY_VERTICES, true),
        frameRate = prefs.getInt(KEY_FRAME_RATE, 24),
        clockEnabled = prefs.getBoolean(KEY_CLOCK_ENABLED, false),
        clockTwoLines = prefs.getBoolean(KEY_CLOCK_TWO_LINES, false),
        clockScale = prefs.getFloat(KEY_CLOCK_SCALE, 0.65f),
        clockPositionX = prefs.getFloat(KEY_CLOCK_X, 0.5f),
        clockPositionY = prefs.getFloat(KEY_CLOCK_Y, 0.5f),
        clockOutline = prefs.getFloat(KEY_CLOCK_OUTLINE, 0f),
        clockInvert = prefs.getBoolean(KEY_CLOCK_INVERT, false)
    )

    fun save(config: MatrixConfig) {
        prefs.edit()
            .putString(KEY_EFFECT, config.effect.name)
            .putString(KEY_SOLID, config.solid.name)
            .putFloat(KEY_SPEED, config.speed)
            .putBoolean(KEY_AUTO_ROTATE_X, config.autoRotateX)
            .putBoolean(KEY_AUTO_ROTATE_Y, config.autoRotateY)
            .putBoolean(KEY_AUTO_ROTATE_Z, config.autoRotateZ)
            .putBoolean(KEY_ACCELEROMETER, config.accelerometer)
            .putFloat(KEY_SENSOR_STRENGTH, config.sensorStrength)
            .putFloat(KEY_BRIGHTNESS, config.brightness)
            .putFloat(KEY_INTENSITY, config.intensity)
            .putFloat(KEY_TRAIL, config.trail)
            .putInt(KEY_PARTICLES, config.particleCount)
            .putBoolean(KEY_VERTICES, config.showVertices)
            .putInt(KEY_FRAME_RATE, config.frameRate)
            .putBoolean(KEY_CLOCK_ENABLED, config.clockEnabled)
            .putBoolean(KEY_CLOCK_TWO_LINES, config.clockTwoLines)
            .putFloat(KEY_CLOCK_SCALE, config.clockScale)
            .putFloat(KEY_CLOCK_X, config.clockPositionX)
            .putFloat(KEY_CLOCK_Y, config.clockPositionY)
            .putFloat(KEY_CLOCK_OUTLINE, config.clockOutline)
            .putBoolean(KEY_CLOCK_INVERT, config.clockInvert)
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
        const val KEY_AUTO_ROTATE_X = "auto_rotate_x"
        const val KEY_AUTO_ROTATE_Y = "auto_rotate_y"
        const val KEY_AUTO_ROTATE_Z = "auto_rotate_z"
        const val KEY_ACCELEROMETER = "accelerometer"
        const val KEY_SENSOR_STRENGTH = "sensor_strength"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_INTENSITY = "intensity"
        const val KEY_TRAIL = "trail"
        const val KEY_PARTICLES = "particles"
        const val KEY_VERTICES = "vertices"
        const val KEY_FRAME_RATE = "frame_rate"
        const val KEY_CLOCK_ENABLED = "clock_enabled"
        const val KEY_CLOCK_TWO_LINES = "clock_two_lines"
        const val KEY_CLOCK_SCALE = "clock_scale"
        const val KEY_CLOCK_X = "clock_x"
        const val KEY_CLOCK_Y = "clock_y"
        const val KEY_CLOCK_OUTLINE = "clock_outline"
        const val KEY_CLOCK_INVERT = "clock_invert"
    }
}
