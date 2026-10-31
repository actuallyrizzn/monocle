package com.actuallyrizzn.monocle

import android.content.Context

class RecordLimitPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("monocle_record", Context.MODE_PRIVATE)

    fun seconds(): Double {
        val stored = prefs.getFloat(KEY, RecordLimit.DEFAULT.toFloat()).toDouble()
        return RecordLimit.clamp(stored)
    }

    fun save(seconds: Double) {
        prefs.edit().putFloat(KEY, RecordLimit.clamp(seconds).toFloat()).apply()
    }

    companion object {
        private const val KEY = "limit_seconds"
    }
}
