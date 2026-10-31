package com.actuallyrizzn.monocle

/** Install hud.py once, then paint over the raw data service. */
class HudSession(private val ble: MonocleBleClient) {
    @Volatile
    var armed: Boolean = false
        private set

    private var epoch: Int = -1

    fun ensure(): Boolean {
        if (!ble.isConnected()) {
            armed = false
            epoch = -1
            return false
        }
        if (armed && epoch == ble.connectionEpoch()) return true
        val ver = ble.exec(HudScript.CHECK, timeoutMs = 8_000)
        if (!ver.contains(HudScript.VERSION)) {
            armed = false
            epoch = -1
            return false
        }
        val out = ble.exec(HudScript.ARM, timeoutMs = 8_000)
        armed = out.contains("OP_OK")
        epoch = if (armed) ble.connectionEpoch() else -1
        return armed
    }

    /**
     * Write op.py in small raw-REPL pastes. Interrupt only on the first exec.
     * Never call this from camera(). Never loop HTTP /v1/repl to do this.
     */
    fun install(): String {
        if (!ble.isConnected()) {
            armed = false
            epoch = -1
            return "ERROR: not connected"
        }
        val already = ble.exec(HudScript.CHECK, timeoutMs = 8_000, interrupt = true)
        if (already.startsWith("ERROR:")) {
            armed = false
            epoch = -1
            return already
        }
        if (already.contains(HudScript.VERSION)) {
            return armLocked(interrupt = false)
        }
        val probe = ble.exec(HudScript.PROBE, timeoutMs = 8_000, interrupt = false)
        if (HudScript.replFailed(probe) || !probe.contains("PROBE")) {
            armed = false
            epoch = -1
            return "ERROR: probe failed $probe"
        }
        for (cmd in HudScript.writePlan()) {
            val out = ble.exec(cmd, timeoutMs = 8_000, interrupt = false)
            if (HudScript.replFailed(out)) {
                armed = false
                epoch = -1
                return "ERROR: write failed $out"
            }
        }
        val ver = ble.exec(HudScript.CHECK, timeoutMs = 8_000, interrupt = false)
        if (!ver.contains(HudScript.VERSION)) {
            armed = false
            epoch = -1
            return "ERROR: missing after write $ver"
        }
        return armLocked(interrupt = false)
    }

    private fun armLocked(interrupt: Boolean): String {
        val out = ble.exec(HudScript.ARM, timeoutMs = 12_000, interrupt = interrupt)
        armed = out.contains("OP_OK")
        epoch = if (armed) ble.connectionEpoch() else -1
        return if (armed) out else "ERROR: arm failed $out"
    }

    fun rearmAfterExec() {
        if (!ble.isConnected()) {
            armed = false
            return
        }
        val ver = ble.exec(HudScript.CHECK, timeoutMs = 4_000, interrupt = false)
        if (!ver.contains(HudScript.VERSION)) {
            armed = false
            epoch = -1
            return
        }
        val out = ble.exec(HudScript.ARM, interrupt = false)
        armed = out.contains("OP_OK")
        epoch = if (armed) ble.connectionEpoch() else -1
    }

    fun show(subject: String, sender: String, body: String): Boolean {
        if (!ensure()) return false
        val payload = CardWire.payload(subject, sender, body)
        for (frame in CardWire.frames(payload)) {
            if (!ble.writeRaw(frame)) {
                armed = false
                return false
            }
        }
        return true
    }

    fun clear(): Boolean {
        if (!ble.isConnected() || !armed || epoch != ble.connectionEpoch()) return false
        return ble.writeRaw(CardWire.clear())
    }

    fun brightness(level: Int): Boolean {
        if (!ble.isConnected() || !armed || epoch != ble.connectionEpoch()) return false
        return ble.writeRaw(CardWire.brightness(level))
    }

    fun led(color: String, on: Boolean): Boolean {
        if (!ensure()) return false
        return ble.writeRaw(CardWire.led(color, on))
    }

    fun collect(
        frame: ByteArray,
        quietMs: Long,
        deadlineMs: Long,
        emptyMs: Long = deadlineMs,
    ): ReplResult? {
        if (!ensure()) return null
        return ble.writeRawAndCollect(frame, quietMs, deadlineMs, emptyMs)
    }

    fun camera(): ReplResult? {
        // A picture must not install op.py. writeAndArm over REPL reboots this firmware.
        if (!ble.isConnected() || !armed || epoch != ble.connectionEpoch()) return null
        return ble.writeRawAndCollect(
            CardWire.camera(),
            quietMs = 800,
            deadlineMs = 45_000,
            emptyMs = 15_000,
        )
    }

    fun mic(seconds: Double): ReplResult? {
        val sec = RecordLimit.clamp(seconds)
        val wait = (sec * 1000).toLong()
        return collect(
            CardWire.mic(sec),
            quietMs = 800,
            deadlineMs = wait + 15_000L,
            emptyMs = wait + 4_000L,
        )
    }

    fun sensorsText(): String? {
        val collected = collect(CardWire.sensors(), quietMs = 200, deadlineMs = 4_000, emptyMs = 2_000)
            ?: return null
        return CardWire.taggedPayload(collected.packets, "sns:").ifBlank { null }
    }

    fun touchText(): String? {
        val collected = collect(CardWire.touch(), quietMs = 200, deadlineMs = 4_000, emptyMs = 2_000)
            ?: return null
        return CardWire.taggedPayload(collected.packets, "tch:").ifBlank { null }
    }
}
