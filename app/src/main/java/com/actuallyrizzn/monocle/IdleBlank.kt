package com.actuallyrizzn.monocle

import java.util.concurrent.atomic.AtomicInteger

/** After a card, blank the waveguide. BLE stays up. */
class IdleBlank(
    private val delayMs: Long = DELAY_MS,
    private val clear: () -> Unit,
) {
    companion object {
        const val DELAY_MS = 20_000L
    }

    private val gen = AtomicInteger(0)

    fun bump() {
        val n = gen.incrementAndGet()
        Thread({
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                return@Thread
            }
            if (gen.get() == n) {
                try {
                    clear()
                } catch (_: Exception) {
                }
            }
        }, "monocle-idle").apply {
            isDaemon = false
            start()
        }
    }

    fun cancel() {
        gen.incrementAndGet()
    }
}
