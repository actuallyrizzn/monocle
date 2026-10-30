package com.actuallyrizzn.monocle

/** Screen and notification copy. Never say the lens is connected unless the flag is true. */
object BridgeStatus {
    fun lensLine(connected: Boolean, deviceName: String?): String {
        return if (connected) {
            val who = deviceName?.trim().orEmpty()
            if (who.isEmpty()) "lens: connected" else "lens: connected · $who"
        } else {
            "lens: not connected"
        }
    }

    fun detail(connected: Boolean, raw: String): String {
        val s = raw.trim()
        if (connected) return s.ifBlank { "connected" }
        if (s.startsWith("connected")) return "disconnected"
        return s.ifBlank { "disconnected" }
    }

    fun screen(
        version: String,
        serviceRunning: Boolean,
        bluetoothGranted: Boolean,
        lensConnected: Boolean,
        rawStatus: String,
        deviceName: String?,
        presenceOn: Boolean?,
        presenceError: String?,
    ): String {
        val svc = if (serviceRunning) "service running" else "service stopped"
        val perm = if (bluetoothGranted) "bluetooth granted" else "bluetooth NOT granted"
        val presence = when {
            presenceOn == null -> "presence idle"
            presenceOn -> "Moya: on"
            else -> "Moya: off" + (presenceError?.let { " · $it" } ?: "")
        }
        return buildString {
            append("version ").append(version).append('\n')
            append(svc).append(" · ").append(perm).append('\n')
            append(lensLine(lensConnected, deviceName)).append('\n')
            append(detail(lensConnected, rawStatus)).append('\n')
            append(presence)
        }
    }

    fun notification(lensConnected: Boolean, version: String): String {
        val lens = if (lensConnected) "lens connected" else "lens not connected"
        return "$version · $lens · :${MonocleCommands.HTTP_PORT}"
    }
}
