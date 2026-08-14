package com.abdulkus.glyphlab.glyph

import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockOutputPolicyTest {
    @Test
    fun ordinaryClockStaysVisibleWhileUnlocked() {
        val mode = resolveMatrixOutputMode(
            MatrixConfig(effect = EffectType.CLOCK),
            isInteractive = true,
            isDeviceLocked = false
        )

        assertEquals(MatrixOutputMode.CLOCK, mode)
    }

    @Test
    fun lockOnlyClockIsOffWhileUnlocked() {
        val mode = resolveMatrixOutputMode(
            MatrixConfig(effect = EffectType.CLOCK, clockLockScreenOnly = true),
            isInteractive = true,
            isDeviceLocked = false
        )

        assertEquals(MatrixOutputMode.OFF, mode)
    }

    @Test
    fun lockOnlyClockIsVisibleOnKeyguardOrSleepingDisplay() {
        val config = MatrixConfig(effect = EffectType.CLOCK, clockLockScreenOnly = true)

        assertEquals(
            MatrixOutputMode.CLOCK,
            resolveMatrixOutputMode(config, isInteractive = true, isDeviceLocked = true)
        )
        assertEquals(
            MatrixOutputMode.CLOCK,
            resolveMatrixOutputMode(config, isInteractive = false, isDeviceLocked = false)
        )
    }

    @Test
    fun existingSleepClockStillReplacesOnlySleepingEffects() {
        val config = MatrixConfig(effect = EffectType.FIRE, sleepClockEnabled = true)

        assertEquals(
            MatrixOutputMode.EFFECT,
            resolveMatrixOutputMode(config, isInteractive = true, isDeviceLocked = false)
        )
        assertEquals(
            MatrixOutputMode.CLOCK,
            resolveMatrixOutputMode(config, isInteractive = false, isDeviceLocked = true)
        )
    }
}
