package com.actuallyrizzn.monocle

/** Phone → glasses card frames. Glass wraps; this is payload only. */
object CardWire {
    const val SEP: Byte = 0x1E
    const val MAX_PACKET = 120

    fun payload(subject: String, sender: String, body: String): ByteArray {
        val s = LensText.clip(subject.ifBlank { LensText.DEFAULT_SUBJECT }, LensText.SUBJ_COLS)
        val w = LensText.clip(sender.ifBlank { LensText.DEFAULT_FROM }, LensText.COLS)
        val b = body.ifBlank { " " }.take(1024)
        val out = java.io.ByteArrayOutputStream()
        out.write(s.toByteArray(Charsets.UTF_8))
        out.write(SEP.toInt() and 0xFF)
        out.write(w.toByteArray(Charsets.UTF_8))
        out.write(SEP.toInt() and 0xFF)
        out.write(b.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    fun frames(payload: ByteArray, maxPacket: Int = MAX_PACKET): List<ByteArray> {
        val room = (maxPacket - 4).coerceAtLeast(16)
        if (payload.size <= room) {
            return listOf(tag("crd:") + payload)
        }
        val frames = ArrayList<ByteArray>()
        var i = 0
        while (i < payload.size) {
            val end = minOf(i + room, payload.size)
            val last = end == payload.size
            val prefix = if (last) "end:" else "dat:"
            frames += tag(prefix) + payload.copyOfRange(i, end)
            i = end
        }
        return frames
    }

    fun clear(): ByteArray = tag("clr:")

    fun brightness(level: Int): ByteArray =
        tag("brt:") + level.coerceIn(0, 4).toString().toByteArray(Charsets.US_ASCII)

    fun camera(): ByteArray = tag("cam:")

    fun mic(seconds: Double): ByteArray {
        val sec = String.format(
            java.util.Locale.US,
            "%.1f",
            RecordLimit.clamp(seconds),
        )
        return tag("mic:") + sec.toByteArray(Charsets.US_ASCII)
    }

    fun sensors(): ByteArray = tag("sns:")

    fun touch(): ByteArray = tag("tqs:")

    fun led(color: String, on: Boolean): ByteArray {
        val c = if (color.equals("RED", true)) "R" else "G"
        val n = if (on) "1" else "0"
        return tag("led:") + "$c$n".toByteArray(Charsets.US_ASCII)
    }

    fun taggedPayload(packets: List<ByteArray>, tag: String): String {
        val prefix = tag.toByteArray(Charsets.US_ASCII)
        val out = StringBuilder()
        for (p in packets) {
            if (p.size >= prefix.size && p.copyOfRange(0, prefix.size).contentEquals(prefix)) {
                out.append(String(p, prefix.size, p.size - prefix.size, Charsets.UTF_8))
            }
        }
        return out.toString()
    }

    private fun tag(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)
}
