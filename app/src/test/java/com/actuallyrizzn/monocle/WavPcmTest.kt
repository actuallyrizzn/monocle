package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavPcmTest {
    @Test
    fun headerAndSize() {
        val pcm = ByteArray(800) { 0 }
        val wav = WavPcm.wrapSigned8(pcm, 8000)
        assertEquals(44 + 800, wav.size)
        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertTrue(wav[20] == 1.toByte())
    }
}
