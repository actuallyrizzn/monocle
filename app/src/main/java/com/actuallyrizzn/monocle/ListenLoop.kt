package com.actuallyrizzn.monocle

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ListenLoop(
    private val ble: MonocleBleClient,
) {
    private val running = AtomicBoolean(false)
    private val threadRef = AtomicReference<Thread?>(null)
    private val last = AtomicReference<Utterance?>(null)
    @Volatile
    var windowSeconds: Double = 2.0
        private set
    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var recorder: ((Double) -> Utterance)? = null

    fun isRunning(): Boolean = running.get()

    fun lastUtterance(): Utterance? = last.get()

    fun start(windowSeconds: Double = 2.0) {
        this.windowSeconds = windowSeconds.coerceIn(0.8, 3.0)
        if (!running.compareAndSet(false, true)) return
        lastError = null
        val t = Thread({
            while (running.get()) {
                try {
                    val rec = recorder
                    val u = if (rec != null) {
                        rec(this.windowSeconds)
                    } else {
                        MicCapture.record(ble, this.windowSeconds, 8000, 8)
                    }
                    last.set(u)
                    lastError = null
                } catch (e: Exception) {
                    lastError = e.message ?: "listen failed"
                }
            }
        }, "monocle-listen")
        threadRef.set(t)
        t.start()
    }

    fun stop() {
        running.set(false)
        threadRef.get()?.interrupt()
        threadRef.set(null)
    }
}
