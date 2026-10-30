package com.actuallyrizzn.monocle

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavPcm {
    fun wrapSigned8(pcm: ByteArray, sampleRate: Int): ByteArray {
        val unsigned = ByteArray(pcm.size) { i -> (pcm[i].toInt() + 128).toByte() }
        return wrap(unsigned, sampleRate, bits = 8)
    }

    fun wrapSigned16le(pcm: ByteArray, sampleRate: Int): ByteArray {
        return wrap(pcm, sampleRate, bits = 16)
    }

    private fun wrap(pcm: ByteArray, sampleRate: Int, bits: Int): ByteArray {
        val channels = 1
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val dataSize = pcm.size
        val out = ByteArrayOutputStream(44 + dataSize)
        val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        hdr.put("RIFF".toByteArray(Charsets.US_ASCII))
        hdr.putInt(36 + dataSize)
        hdr.put("WAVE".toByteArray(Charsets.US_ASCII))
        hdr.put("fmt ".toByteArray(Charsets.US_ASCII))
        hdr.putInt(16)
        hdr.putShort(1)
        hdr.putShort(channels.toShort())
        hdr.putInt(sampleRate)
        hdr.putInt(byteRate)
        hdr.putShort(blockAlign.toShort())
        hdr.putShort(bits.toShort())
        hdr.put("data".toByteArray(Charsets.US_ASCII))
        hdr.putInt(dataSize)
        out.write(hdr.array())
        out.write(pcm)
        return out.toByteArray()
    }
}
