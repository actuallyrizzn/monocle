package com.actuallyrizzn.monocle

import android.content.Context

/** Last successful Monocle GATT address. Direct reconnect works in the background; a fresh scan often does not. */
class BlePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("monocle_ble", Context.MODE_PRIVATE)

    fun lastAddress(): String? =
        prefs.getString(KEY_ADDR, null)?.trim()?.takeIf { it.contains(':') }

    fun saveAddress(address: String?) {
        val a = address?.trim().orEmpty()
        if (!a.contains(':')) return
        prefs.edit().putString(KEY_ADDR, a).apply()
    }

    companion object {
        private const val KEY_ADDR = "last_address"
    }
}
