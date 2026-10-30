package com.actuallyrizzn.monocle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DeviceMatchTest {
    @Test
    fun nameMonocle() {
        assertTrue(DeviceMatch.isCandidate("monocle"))
        assertTrue(DeviceMatch.isCandidate("Monocle AR"))
        assertTrue(DeviceMatch.isCandidate("Brilliant Labs"))
    }

    @Test
    fun nusUuidWithoutName() {
        assertTrue(DeviceMatch.isCandidate(null, listOf(DeviceMatch.NUS)))
    }

    @Test
    fun rejectsUnrelated() {
        assertFalse(DeviceMatch.isCandidate(null))
        assertFalse(DeviceMatch.isCandidate(""))
        assertFalse(DeviceMatch.isCandidate("Pixel Buds"))
        assertFalse(DeviceMatch.isCandidate("watch", listOf(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))))
    }
}
