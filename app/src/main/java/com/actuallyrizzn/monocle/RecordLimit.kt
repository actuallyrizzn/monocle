package com.actuallyrizzn.monocle

/** Hard cap on a glasses-mic take. No VAD endpointer — record this long, then stop. */
object RecordLimit {
    const val MIN = 1.0
    const val MAX = 8.0
    const val DEFAULT = 4.0

    fun clamp(seconds: Double): Double {
        if (seconds.isNaN()) return DEFAULT
        return seconds.coerceIn(MIN, MAX)
    }

    /** Requested take, never above the app cap. Missing request uses the cap. */
    fun apply(requested: Double?, cap: Double): Double {
        val bound = clamp(cap)
        val want = requested ?: bound
        return minOf(clamp(want), bound)
    }
}
