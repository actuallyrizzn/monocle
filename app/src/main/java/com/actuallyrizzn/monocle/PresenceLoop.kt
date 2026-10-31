package com.actuallyrizzn.monocle

/**
 * Phone → presence heartbeat + pull. Fail-closed: missing config or an
 * unreachable host leaves the flag off. Only paint when BLE is actually up.
 */
class PresenceLoop(
    private val client: PresenceClient,
    private val connected: () -> Boolean,
    private val deviceName: () -> String?,
    private val paint: (text: String, subject: String, sender: String) -> Boolean,
    /** When a queued card asks for a reply, record a fixed take. Null = paint only. */
    private val ask: ((text: String, subject: String, sender: String) -> Boolean)? = null,
    private val intervalMs: Long = INTERVAL_MS,
) {
    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var lastOn: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var lastShown: Int = 0
        private set

    @Volatile
    var lastHeartbeatOk: Boolean = false
        private set

    private var thread: Thread? = null

    companion object {
        const val INTERVAL_MS = 15_000L
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            while (running) {
                try {
                    tick()
                } catch (e: Exception) {
                    lastHeartbeatOk = false
                    lastOn = false
                    lastError = e.message ?: "tick_failed"
                }
                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "monocle-presence").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        lastOn = false
    }

    fun tick(): Int {
        if (!PresencePrefs.isConfigured(client.baseUrl, client.apiKey)) {
            lastHeartbeatOk = false
            lastOn = false
            lastError = "presence_not_configured"
            return 0
        }
        val ble = connected()
        val hb = client.heartbeat(ble, deviceName())
        lastHeartbeatOk = hb.ok
        lastOn = hb.ok && hb.on && ble
        lastError = hb.error
        if (!hb.ok || !ble || !hb.on) {
            return 0
        }
        if (hb.queueLen == 0) {
            lastShown = 0
            return 0
        }
        val pulled = client.pull()
        if (!pulled.ok) {
            lastError = pulled.error
            return 0
        }
        val ids = mutableListOf<Int>()
        for (msg in pulled.messages) {
            val ok = try {
                if (msg.listen && ask != null) {
                    ask.invoke(msg.text, msg.subject, msg.sender)
                } else {
                    paint(msg.text, msg.subject, msg.sender)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "paint_failed"
                false
            }
            if (ok && msg.id != 0) ids += msg.id
        }
        if (ids.isNotEmpty()) {
            val ack = client.ack(ids)
            if (!ack.ok) {
                lastError = ack.error
            }
        }
        lastShown = ids.size
        return ids.size
    }
}
