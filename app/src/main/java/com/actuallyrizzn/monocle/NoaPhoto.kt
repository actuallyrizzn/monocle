package com.actuallyrizzn.monocle

import java.io.ByteArrayOutputStream

/** Phone-side of Noa `ist:` / `idt:` / `ien:` (BaseActivity / Controller.swift). */
object NoaPhoto {
    fun assemble(packets: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        var tagged = false
        for (p in packets) {
            if (p.isEmpty()) continue
            val tag = if (p.size >= 4) String(p, 0, 4, Charsets.ISO_8859_1) else ""
            when (tag) {
                "ist:" -> tagged = true
                "ien:" -> return out.toByteArray()
                "idt:" -> {
                    tagged = true
                    if (p.size > 4) out.write(p, 4, p.size - 4)
                }
                else -> out.write(p)
            }
        }
        val bytes = out.toByteArray()
        return jpegSlice(bytes) ?: if (tagged) bytes else jpegSlice(concat(packets)) ?: bytes
    }

    fun assembleBlob(raw: ByteArray): ByteArray = jpegSlice(raw) ?: raw

    private fun concat(packets: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in packets) out.write(p)
        return out.toByteArray()
    }

    private fun jpegSlice(raw: ByteArray): ByteArray? {
        val soi = indexOf(raw, 0xFF.toByte(), 0xD8.toByte())
        if (soi < 0) return null
        val eoi = lastIndexOf(raw, 0xFF.toByte(), 0xD9.toByte(), soi)
        return if (eoi > soi) raw.copyOfRange(soi, eoi + 2) else raw.copyOfRange(soi, raw.size)
    }

    private fun indexOf(raw: ByteArray, a: Byte, b: Byte): Int {
        var i = 0
        while (i + 1 < raw.size) {
            if (raw[i] == a && raw[i + 1] == b) return i
            i += 1
        }
        return -1
    }

    private fun lastIndexOf(raw: ByteArray, a: Byte, b: Byte, from: Int): Int {
        var i = raw.size - 2
        while (i >= from) {
            if (raw[i] == a && raw[i + 1] == b) return i
            i -= 1
        }
        return -1
    }
}
