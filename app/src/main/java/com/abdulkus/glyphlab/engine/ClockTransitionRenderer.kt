package com.abdulkus.glyphlab.engine

import com.abdulkus.glyphlab.data.ClockTransition
import kotlin.math.*

/** Composites a frozen clock over a destination that keeps animating during the transition. */
class ClockTransitionRenderer {
    private var source: IntArray? = null
    private var started = 0L
    private var targetLayout: IntArray? = null
    private var style = ClockTransition.DISSOLVE
    private var sequence = 0
    val active get() = source != null

    fun start(clock: IntArray, selected: ClockTransition, nowNanos: Long) {
        if (selected == ClockTransition.NONE) { cancel(); return }
        source = clock.copyOf()
        targetLayout = null
        started = nowNanos
        style = if (selected == ClockTransition.RANDOM) STYLES[sequence++ % STYLES.size] else selected
    }
    fun cancel() { source = null; targetLayout = null }
    fun frame(destination: IntArray, nowNanos: Long): IntArray {
        val clock = source ?: return destination
        val p = ((nowNanos - started) / 950_000_000.0).coerceIn(0.0, 1.0)
        if (p >= 1) { cancel(); return destination }
        if (targetLayout == null) targetLayout = destination.copyOf()
        return compose(clock, destination, style, p, targetLayout!!)
    }

    companion object {
        val STYLES = listOf(ClockTransition.DISSOLVE, ClockTransition.SPLIT, ClockTransition.REASSEMBLE, ClockTransition.RIPPLE)
        fun compose(clock: IntArray, destination: IntArray, style: ClockTransition, progress: Double, destinationLayout: IntArray = destination): IntArray {
            val p = progress.coerceIn(0.0, 1.0)
            if (p <= 0) return clock.copyOf()
            if (p >= 1 || style == ClockTransition.NONE) return destination.copyOf()
            val f = IntArray(169)
            val eased = p * p * (3 - 2 * p)
            val targets = destinationLayout.indices.filter { destinationLayout[it] >= 50 }.ifEmpty { listOf(84) }
            val sources = clock.indices.filter { clock[it] > 0 }
            for (i in f.indices) {
                if (!MatrixEngine.isInsideMatrix(i % 13, i / 13)) continue
                val rank = ((i * 73 + i / 13 * 29) % 137) / 137.0
                val reveal = when (style) {
                    ClockTransition.DISSOLVE, ClockTransition.RANDOM -> ((p - rank * 0.65) / 0.35).coerceIn(0.0, 1.0)
                    ClockTransition.RIPPLE -> ((p * 9 - hypot(i % 13 - 6.0, i / 13 - 6.0)) / 2).coerceIn(0.0, 1.0)
                    else -> eased
                }
                f[i] = (destination[i] * reveal).roundToInt()
                if (style == ClockTransition.DISSOLVE || style == ClockTransition.RANDOM || style == ClockTransition.RIPPLE) {
                    f[i] = max(f[i], (clock[i] * (1 - reveal)).roundToInt())
                }
            }
            if (style == ClockTransition.SPLIT || style == ClockTransition.REASSEMBLE) {
                sources.forEachIndexed { order, i ->
                    val x = i % 13; val y = i / 13
                    val target = targets[order * targets.size / sources.size]
                    val px = if (style == ClockTransition.SPLIT) x + (if (x < 6) -1 else 1) * eased * 8 else x + (target % 13 - x) * eased
                    val py = if (style == ClockTransition.SPLIT) y + (if (y < 6) -1 else 1) * eased * 3 else y + (target / 13 - y) * eased
                    splat(f, px, py, clock[i] * (1 - eased))
                }
            }
            return f
        }
        private fun splat(f: IntArray, x: Double, y: Double, value: Double) {
            val px = x.roundToInt(); val py = y.roundToInt()
            if (MatrixEngine.isInsideMatrix(px, py)) {
                f[py * 13 + px] = max(f[py * 13 + px], value.roundToInt().coerceIn(0, 255))
            }
        }
    }
}
