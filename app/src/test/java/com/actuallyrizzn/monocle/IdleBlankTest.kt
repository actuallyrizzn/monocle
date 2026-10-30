package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class IdleBlankTest {
    @Test
    fun firesAfterDelay() {
        val hits = AtomicInteger(0)
        val idle = IdleBlank(delayMs = 80) { hits.incrementAndGet() }
        idle.bump()
        Thread.sleep(200)
        assertEquals(1, hits.get())
    }

    @Test
    fun bumpResetsTheTimer() {
        val hits = AtomicInteger(0)
        val idle = IdleBlank(delayMs = 120) { hits.incrementAndGet() }
        idle.bump()
        Thread.sleep(60)
        idle.bump()
        Thread.sleep(80)
        assertEquals(0, hits.get())
        Thread.sleep(80)
        assertEquals(1, hits.get())
    }

    @Test
    fun cancelSkipsTheBlank() {
        val hits = AtomicInteger(0)
        val idle = IdleBlank(delayMs = 50) { hits.incrementAndGet() }
        idle.bump()
        idle.cancel()
        Thread.sleep(120)
        assertEquals(0, hits.get())
    }
}
