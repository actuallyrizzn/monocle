package com.actuallyrizzn.monocle

import java.io.ByteArrayOutputStream

/** Assemble Noa-tagged BLE frames (`ist:`/`idt:`/`ien:`, `ast:`/`dat:`/`aen:`). */
object NoaFramed {
    fun assemble(
        packets: List<ByteArray>,
        start: String,
        data: String,
        end: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in packets) {
            if (p.isEmpty()) continue
            val tag = if (p.size >= 4) String(p, 0, 4, Charsets.ISO_8859_1) else ""
            when (tag) {
                start -> {}
                end -> return out.toByteArray()
                data -> if (p.size > 4) out.write(p, 4, p.size - 4)
                else -> out.write(p)
            }
        }
        return out.toByteArray()
    }
}
