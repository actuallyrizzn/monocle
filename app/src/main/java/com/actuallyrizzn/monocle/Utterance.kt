package com.actuallyrizzn.monocle

data class Utterance(
    val pcm: ByteArray,
    val sampleRate: Int,
    val bitDepth: Int,
    val energy: Double,
    val speaking: Boolean,
    val atMs: Long,
    val repl: String,
) {
    fun wav(): ByteArray {
        return if (bitDepth >= 12) {
            WavPcm.wrapSigned16le(pcm, sampleRate)
        } else {
            WavPcm.wrapSigned8(pcm, sampleRate)
        }
    }
}
