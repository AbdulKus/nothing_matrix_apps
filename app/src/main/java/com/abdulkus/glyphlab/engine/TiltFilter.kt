package com.abdulkus.glyphlab.engine

import kotlin.math.*

/** Frame-rate-independent gravity smoothing. Android gravity/accelerometer share axis signs. */
class TiltFilter(private val responseSeconds: Double = 0.22) {
    data class Tilt(val x: Float, val y: Float)
    private var last = 0L
    private var x = 0.0
    private var y = 0.0
    fun reset() { last = 0; x = 0.0; y = 0.0 }
    fun update(targetX: Float, targetY: Float, nanos: Long): Tilt {
        val dt = if (last == 0L) 0.02 else ((nanos - last) / 1e9).coerceIn(0.0, 0.1)
        last = nanos
        val a = 1 - exp(-dt / responseSeconds)
        fun target(v: Float): Double {
            val n = v.coerceIn(-1f, 1f).toDouble()
            return sign(n) * ((abs(n) - 0.015).coerceAtLeast(0.0) / 0.985)
        }
        x += a * (target(targetX) - x); y += a * (target(targetY) - y)
        return Tilt(x.toFloat(), y.toFloat())
    }
}
