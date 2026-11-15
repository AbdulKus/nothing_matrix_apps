package com.abdulkus.glyphlab.util

import android.os.Build
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph

object DeviceUtils {
    /**
     * Determines whether the current device is Nothing Phone (4a) Pro with a physical Glyph Matrix.
     * Regular Nothing Phone (4a) (model A069 / DEVICE_25111) does NOT have a matrix display.
     */
    fun isPhone4aPro(): Boolean {
        return runCatching {
            // If the SDK explicitly flags this as regular 4a without matrix (A069), reject immediately
            if (Common.is25111()) return false

            // If Ketchum SDK identifies the device as 4a Pro (A069P / DEVICE_25111p)
            if (Common.is25111p()) return true

            // If the Ketchum SDK reports a 13x13 matrix hardware length
            if (Common.getDeviceMatrixLength() == Glyph.DEVICE_25111p_MATRIX_LENGTH) return true

            is4aProModel(Build.MODEL, Build.DEVICE, Build.PRODUCT)
        }.getOrDefault(false)
    }

    internal fun is4aProModel(model: String?, device: String?, product: String?): Boolean {
        val m = model.orEmpty().trim()
        val d = device.orEmpty().trim()
        val p = product.orEmpty().trim()

        // Explicit non-Pro 4a model code
        if (m.equals(Glyph.DEVICE_25111, ignoreCase = true) ||
            d.equals(Glyph.DEVICE_25111, ignoreCase = true) ||
            p.equals(Glyph.DEVICE_25111, ignoreCase = true)
        ) {
            return false
        }

        // Explicit 4a Pro model code (A069P)
        if (m.equals(Glyph.DEVICE_25111p, ignoreCase = true) ||
            d.equals(Glyph.DEVICE_25111p, ignoreCase = true) ||
            p.equals(Glyph.DEVICE_25111p, ignoreCase = true)
        ) {
            return true
        }

        fun has4aProTokens(s: String): Boolean {
            val lower = s.lowercase()
            val has4a = lower.contains("4a") || lower.contains("4 a")
            val hasPro = lower.contains("pro")
            return has4a && hasPro
        }

        return has4aProTokens(m) || has4aProTokens(d) || has4aProTokens(p)
    }
}
