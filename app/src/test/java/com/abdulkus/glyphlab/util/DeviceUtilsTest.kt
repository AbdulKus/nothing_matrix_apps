package com.abdulkus.glyphlab.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUtilsTest {
    @Test
    fun regularNothing4a_isRejected() {
        assertFalse(DeviceUtils.is4aProModel("A069", "A069", "A069"))
        assertFalse(DeviceUtils.is4aProModel("Nothing Phone (4a)", "A069", "A069"))
        assertFalse(DeviceUtils.is4aProModel("Phone (4a)", "Pacman", "A069"))
    }

    @Test
    fun otherNothingPhones_areRejected() {
        assertFalse(DeviceUtils.is4aProModel("A063", "A063", "A063")) // Phone (1)
        assertFalse(DeviceUtils.is4aProModel("A065", "A065", "A065")) // Phone (2)
        assertFalse(DeviceUtils.is4aProModel("A142", "A142", "A142")) // Phone (2a)
    }

    @Test
    fun nonNothingDevices_areRejected() {
        assertFalse(DeviceUtils.is4aProModel("Pixel 8 Pro", "husky", "husky"))
        assertFalse(DeviceUtils.is4aProModel("SM-S928B", "e3q", "e3qxxx"))
        assertFalse(DeviceUtils.is4aProModel("iPhone 15", "iPhone", "iPhone"))
    }

    @Test
    fun nothing4aPro_isRecognized() {
        assertTrue(DeviceUtils.is4aProModel("A069P", "A069P", "A069P"))
        assertTrue(DeviceUtils.is4aProModel("a069p", "a069p", "a069p"))
        assertTrue(DeviceUtils.is4aProModel("Nothing Phone (4a) Pro", "PacmanPro", "A069P"))
        assertTrue(DeviceUtils.is4aProModel("Phone (4a) Pro", "A069P", "A069P"))
    }
}
