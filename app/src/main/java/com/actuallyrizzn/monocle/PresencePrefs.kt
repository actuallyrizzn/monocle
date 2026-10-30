package com.actuallyrizzn.monocle

import android.content.Context

class PresencePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("monocle_presence", Context.MODE_PRIVATE)

    fun url(): String =
        prefs.getString(KEY_URL, null)?.trim().orEmpty().ifBlank { BuildConfig.PRESENCE_URL }

    fun apiKey(): String =
        prefs.getString(KEY_API, null)?.trim().orEmpty().ifBlank { BuildConfig.PRESENCE_API_KEY }

    fun save(url: String?, apiKey: String?) {
        val ed = prefs.edit()
        if (!url.isNullOrBlank()) ed.putString(KEY_URL, url.trim().trimEnd('/'))
        if (!apiKey.isNullOrBlank()) ed.putString(KEY_API, apiKey.trim())
        ed.apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_API = "api_key"
    }
}
