package com.actuallyrizzn.monocle

object ReplParse {
    fun stdout(repl: String): String {
        var s = repl.replace("\r", "")
        if (s.startsWith("OK")) {
            s = s.removePrefix("OK")
        }
        val cut = s.indexOf('\u0004')
        if (cut >= 0) {
            s = s.substring(0, cut)
        }
        return s.trim()
    }

    fun labeled(repl: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (line in stdout(repl).lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val sp = t.indexOf(' ')
            if (sp <= 0) continue
            out[t.substring(0, sp)] = t.substring(sp + 1).trim()
        }
        return out
    }

    fun hexPayload(repl: String): ByteArray? {
        val hex = labeled(repl)["HEX"] ?: return null
        if (hex.length < 2 || hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }
}
