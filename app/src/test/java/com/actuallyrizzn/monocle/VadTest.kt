package com.actuallyrizzn.monocle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VadTest {
    @Test
    fun silenceIsNotSpeech() {
        val pcm = ByteArray(2000) { 1 }
        assertTrue(Vad.rms8(pcm) < 3.0)
        assertFalse(Vad.isSpeech8(pcm))
    }

    @Test
    fun loudIsSpeech() {
        val pcm = ByteArray(2000) { i -> if (i % 2 == 0) 40 else (-40).toByte() }
        assertTrue(Vad.rms8(pcm) > 20.0)
        assertTrue(Vad.isSpeech8(pcm))
    }

    @Test
    fun tooShortIsNotSpeech() {
        assertFalse(Vad.isSpeech8(ByteArray(10) { 40 }))
    }
}
