package com.actuallyrizzn.monocle

object MicCapture {
    fun fromPackets(
        packets: List<ByteArray>,
        raw: ByteArray,
        sampleRate: Int,
        bitDepth: Int,
        repl: String = "",
    ): Utterance {
        val framed = NoaFramed.assemble(packets, "ast:", "dat:", "aen:")
        val pcm = when {
            framed.isNotEmpty() -> framed
            raw.isNotEmpty() -> raw
            else -> ReplParse.hexPayload(repl) ?: ByteArray(0)
        }
        return Utterance(
            pcm = pcm,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            energy = Vad.rms8(pcm),
            speaking = Vad.isSpeech8(pcm),
            atMs = System.currentTimeMillis(),
            repl = repl,
        )
    }

    fun record(
        ble: MonocleBleClient,
        seconds: Double,
        sampleRate: Int,
        bitDepth: Int,
    ): Utterance {
        val sec = seconds.coerceIn(0.5, 4.0)
        val timeout = (sec * 1000).toLong() + 12_000L
        val collected = ble.execCollectRaw(
            MonocleCommands.micRecord(sec, sampleRate, bitDepth),
            timeoutMs = timeout,
        )
        val labels = ReplParse.labeled(collected.repl)
        val rate = labels["RATE"]?.toIntOrNull() ?: sampleRate
        val depth = labels["DEPTH"]?.toIntOrNull() ?: bitDepth
        return fromPackets(collected.packets, collected.raw, rate, depth, collected.repl)
    }
}
