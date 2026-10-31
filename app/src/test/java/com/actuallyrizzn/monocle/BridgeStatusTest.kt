package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeStatusTest {
    @Test
    fun lensLineConnectedOnlyWhenFlagTrue() {
        assertEquals("lens: connected · monocle", BridgeStatus.lensLine(true, "monocle"))
        assertEquals("lens: not connected", BridgeStatus.lensLine(false, "monocle"))
        assertEquals("lens: not connected", BridgeStatus.lensLine(false, null))
    }

    @Test
    fun staleConnectedStatusIsNotShownWhenDisconnected() {
        assertEquals("disconnected", BridgeStatus.detail(false, "connected monocle"))
        assertEquals("connected monocle", BridgeStatus.detail(true, "connected monocle"))
        assertEquals("scanning", BridgeStatus.detail(false, "scanning"))
    }

    @Test
    fun screenNeverSaysConnectedWhenLensIsDown() {
        val text = BridgeStatus.screen(
            version = "0.5.10",
            serviceRunning = true,
            bluetoothGranted = true,
            lensConnected = false,
            rawStatus = "connected monocle",
            deviceName = "monocle",
            presenceOn = false,
            presenceError = "Failed to connect to /127.0.0.1:8890",
        )
        assertTrue(text.startsWith("version 0.5.10"))
        assertTrue(text.contains("lens: not connected"))
        assertTrue(text.contains("presence: off"))
        assertFalse(text.contains("Moya"))
        assertFalse(text.contains("lens: connected"))
        assertFalse(text.lines()[2].contains("connected monocle"))
    }

    @Test
    fun notificationTracksLensFlag() {
        assertEquals("0.5.10 · lens connected · :8767", BridgeStatus.notification(true, "0.5.10"))
        assertEquals("0.5.10 · lens not connected · :8767", BridgeStatus.notification(false, "0.5.10"))
    }
}
