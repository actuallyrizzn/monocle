package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceClientTest {
    @Test
    fun parseHeartbeatOn() {
        val r = PresenceClient.parseHeartbeat("""{"ok":true,"on":true,"ble":true,"queue_len":0}""")
        assertTrue(r.ok)
        assertTrue(r.on)
        assertEquals(0, r.queueLen)
    }

    @Test
    fun parseHeartbeatUnauthorizedIsOff() {
        val r = PresenceClient.parseHeartbeat("""{"ok":false,"error":"unauthorized"}""")
        assertFalse(r.ok)
        assertFalse(r.on)
        assertEquals("unauthorized", r.error)
    }

    @Test
    fun parsePullMessages() {
        val r = PresenceClient.parsePull(
            """{"ok":true,"on":true,"messages":[{"id":7,"text":"Fryer ping","sender":"rico","subject":"Kitchen"}]}""",
        )
        assertTrue(r.ok)
        assertEquals(1, r.messages.size)
        assertEquals(7, r.messages[0].id)
        assertEquals("Fryer ping", r.messages[0].text)
        assertEquals("rico", r.messages[0].sender)
        assertEquals("Kitchen", r.messages[0].subject)
    }

    @Test
    fun parsePullSkipsEmptyText() {
        val r = PresenceClient.parsePull(
            """{"ok":true,"on":true,"messages":[{"id":1,"text":"  "},{"id":2,"text":"keep"}]}""",
        )
        assertEquals(1, r.messages.size)
        assertEquals(2, r.messages[0].id)
    }

    @Test
    fun missingKeyDoesNotCallTransport() {
        var called = 0
        val client = PresenceClient("http://127.0.0.1:8890", "") { _, _, _, _ ->
            called += 1
            "{}"
        }
        val hb = client.heartbeat(true, "monocle")
        assertFalse(hb.ok)
        assertEquals("missing_api_key", hb.error)
        assertEquals(0, called)
    }

    @Test
    fun unreachableIsFailClosed() {
        val client = PresenceClient("http://127.0.0.1:8890", "k") { _, _, _, _ ->
            throw java.net.ConnectException("refused")
        }
        val hb = client.heartbeat(true, "monocle")
        assertFalse(hb.ok)
        assertFalse(hb.on)
        assertTrue(hb.error!!.contains("refused"))
    }
}
