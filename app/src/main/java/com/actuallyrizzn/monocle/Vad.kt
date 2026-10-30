package com.actuallyrizzn.monocle

import kotlin.math.sqrt

object Vad {
    const val DEFAULT_RMS = 8.0
    const val DEFAULT_FRAC = 0.08
    const val ABS_MIN = 12

    fun rms8(pcm: ByteArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sum = 0.0
        for (b in pcm) {
            val v = b.toInt().toDouble()
            sum += v * v
        }
        return sqrt(sum / pcm.size)
    }

    fun speechFraction8(pcm: ByteArray, absMin: Int = ABS_MIN): Double {
        if (pcm.isEmpty()) return 0.0
        var hot = 0
        for (b in pcm) {
            if (kotlin.math.abs(b.toInt()) >= absMin) hot += 1
        }
        return hot.toDouble() / pcm.size
    }

    fun isSpeech8(
        pcm: ByteArray,
        rmsMin: Double = DEFAULT_RMS,
        fracMin: Double = DEFAULT_FRAC,
    ): Boolean {
        if (pcm.size < 400) return false
        return rms8(pcm) >= rmsMin && speechFraction8(pcm) >= fracMin
    }
}
