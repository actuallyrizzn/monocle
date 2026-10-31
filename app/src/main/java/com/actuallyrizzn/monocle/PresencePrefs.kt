package com.actuallyrizzn.monocle

import android.content.Context

/** Presence URL and key live on the phone. Nothing is baked in at build time. */
class PresencePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("monocle_presence", Context.MODE_PRIVATE)

    fun url(): String = normalizeUrl(prefs.getString(KEY_URL, "") ?: "")

    fun apiKey(): String = prefs.getString(KEY_API, "")?.trim().orEmpty()

    fun configured(): Boolean = isConfigured(url(), apiKey())

    fun save(url: String?, apiKey: String?) {
        val ed = prefs.edit()
        if (url != null) {
            val n = normalizeUrl(url)
            if (n.isEmpty()) ed.remove(KEY_URL) else ed.putString(KEY_URL, n)
        }
        if (apiKey != null) {
            val n = apiKey.trim()
            if (n.isEmpty()) ed.remove(KEY_API) else ed.putString(KEY_API, n)
        }
        ed.apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_API = "api_key"

        fun normalizeUrl(url: String): String = url.trim().trimEnd('/')

        fun isConfigured(url: String, apiKey: String): Boolean =
            normalizeUrl(url).isNotBlank() && apiKey.trim().isNotBlank()
    }
}
