package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceLoopTest {
    private class Fake(
        var ble: Boolean = true,
        var heartbeat: String = """{"ok":true,"on":true}""",
        var pull: String = """{"ok":true,"on":true,"messages":[]}""",
        var ack: String = """{"ok":true,"removed":1}""",
    ) {
        val calls = mutableListOf<String>()
        val painted = mutableListOf<Triple<String, String, String>>()
        val client = PresenceClient("http://127.0.0.1:8890", "k") { method, url, _, body ->
            calls += "$method ${url.substringAfterLast('/')}"
            when {
                url.endsWith("/v1/heartbeat") -> heartbeat
                url.endsWith("/v1/pull") -> pull
                url.endsWith("/v1/ack") -> ack
                else -> error("unexpected $method $url $body")
            }
        }
        val loop = PresenceLoop(
            client = client,
            connected = { ble },
            deviceName = { "monocle" },
            paint = { text, subject, sender ->
                painted += Triple(text, subject, sender)
                true
            },
        )
    }

    @Test
    fun paintsAndAcksWhenOn() {
        val f = Fake(
            pull = """{"ok":true,"on":true,"messages":[{"id":3,"text":"Order up","sender":"Otto","subject":"Kitchen"}]}""",
        )
        assertEquals(1, f.loop.tick())
        assertEquals(listOf(Triple("Order up", "Kitchen", "Otto")), f.painted)
        assertTrue(f.loop.lastOn)
        assertTrue(f.calls.contains("POST ack"))
    }

    @Test
    fun noPullWhenBleDown() {
        val f = Fake(ble = false)
        assertEquals(0, f.loop.tick())
        assertEquals(0, f.painted.size)
        assertEquals(listOf("POST heartbeat"), f.calls)
        assertFalse(f.loop.lastOn)
    }

    @Test
    fun skipsPullWhenQueueEmpty() {
        val f = Fake(heartbeat = """{"ok":true,"on":true,"queue_len":0}""")
        assertEquals(0, f.loop.tick())
        assertEquals(0, f.painted.size)
        assertEquals(listOf("POST heartbeat"), f.calls)
        assertTrue(f.loop.lastOn)
    }

    @Test
    fun noPullWhenFlagOff() {
        val f = Fake(heartbeat = """{"ok":true,"on":false,"ble":true}""")
        assertEquals(0, f.loop.tick())
        assertEquals(0, f.painted.size)
        assertEquals(listOf("POST heartbeat"), f.calls)
        assertFalse(f.loop.lastOn)
    }

    @Test
    fun unreachableDoesNotPaint() {
        val f = Fake()
        val client = PresenceClient("http://127.0.0.1:8890", "k") { _, _, _, _ ->
            throw java.io.IOException("timeout")
        }
        val loop = PresenceLoop(
            client = client,
            connected = { true },
            deviceName = { "monocle" },
            paint = { _, _, _ -> error("should not paint") },
        )
        assertEquals(0, loop.tick())
        assertFalse(loop.lastOn)
        assertEquals("timeout", loop.lastError)
    }

    @Test
    fun skipsWhenNotConfigured() {
        val client = PresenceClient("", "") { _, _, _, _ -> error("no http") }
        val loop = PresenceLoop(
            client = client,
            connected = { true },
            deviceName = { "monocle" },
            paint = { _, _, _ -> error("should not paint") },
        )
        assertEquals(0, loop.tick())
        assertFalse(loop.lastOn)
        assertEquals("presence_not_configured", loop.lastError)
    }

    @Test
    fun listenFlagRunsAskNotPaint() {
        val asked = mutableListOf<String>()
        val client = PresenceClient("http://127.0.0.1:8890", "k") { method, url, _, _ ->
            when {
                url.endsWith("/v1/heartbeat") -> """{"ok":true,"on":true}"""
                url.endsWith("/v1/pull") ->
                    """{"ok":true,"on":true,"messages":[{"id":7,"text":"Say the word","sender":"Athena","subject":"Ask","listen":true}]}"""
                url.endsWith("/v1/ack") -> """{"ok":true,"removed":1}"""
                else -> error("$method $url")
            }
        }
        val loop = PresenceLoop(
            client = client,
            connected = { true },
            deviceName = { "monocle" },
            paint = { _, _, _ -> error("status paint should not run") },
            ask = { text, _, _ ->
                asked += text
                true
            },
        )
        assertEquals(1, loop.tick())
        assertEquals(listOf("Say the word"), asked)
    }

    @Test
    fun failedPaintIsNotAcked() {
        val client = PresenceClient("http://127.0.0.1:8890", "k") { method, url, _, _ ->
            when {
                url.endsWith("/v1/heartbeat") -> """{"ok":true,"on":true}"""
                url.endsWith("/v1/pull") ->
                    """{"ok":true,"on":true,"messages":[{"id":9,"text":"nope","sender":"x","subject":"y"}]}"""
                url.endsWith("/v1/ack") -> error("ack should not run")
                else -> error("$method $url")
            }
        }
        val loop = PresenceLoop(
            client = client,
            connected = { true },
            deviceName = { "monocle" },
            paint = { _, _, _ -> false },
        )
        assertEquals(0, loop.tick())
        assertTrue(loop.lastOn)
    }
}
