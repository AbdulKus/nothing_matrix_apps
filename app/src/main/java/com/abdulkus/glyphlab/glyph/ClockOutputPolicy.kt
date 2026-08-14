package com.abdulkus.glyphlab.glyph

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig

internal enum class MatrixOutputMode {
    EFFECT,
    CLOCK,
    OFF
}

/** Resolves the physical Matrix output without changing the on-screen preview. */
internal fun resolveMatrixOutputMode(
    config: MatrixConfig,
    isInteractive: Boolean,
    isDeviceLocked: Boolean
): MatrixOutputMode {
    if (config.effect == EffectType.CLOCK) {
        val lockedOrAsleep = isDeviceLocked || !isInteractive
        return if (!config.clockLockScreenOnly || lockedOrAsleep) {
            MatrixOutputMode.CLOCK
        } else {
            MatrixOutputMode.OFF
        }
    }

    return if (config.sleepClockEnabled && !isInteractive) {
        MatrixOutputMode.CLOCK
    } else {
        MatrixOutputMode.EFFECT
    }
}
