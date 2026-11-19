package com.abdulkus.glyphlab.engine

/** Preserve the effects' established input orientation on the rear-facing matrix.
 * Gravity and accelerometer report the same Android axes, so both use this mapping.
 */
object SensorTilt {
    private const val GRAVITY_EARTH = 9.80665f

    fun fromAndroid(x: Float, y: Float, limit: Float = 1f): TiltFilter.Tilt = TiltFilter.Tilt(
        (-x / GRAVITY_EARTH).coerceIn(-limit, limit),
        (-y / GRAVITY_EARTH).coerceIn(-limit, limit)
    )
}
