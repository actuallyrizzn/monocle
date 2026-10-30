package com.actuallyrizzn.monocle

import java.util.UUID

object DeviceMatch {
    val NUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    fun isCandidate(name: String?, serviceUuids: Collection<UUID>? = null): Boolean {
        if (serviceUuids?.any { it == NUS } == true) return true
        val n = name?.trim()?.lowercase() ?: return false
        return n.contains("monocle") || n.contains("brilliant")
    }
}
