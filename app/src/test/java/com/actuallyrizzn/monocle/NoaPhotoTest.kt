package com.actuallyrizzn.monocle

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NoaPhotoTest {
    @Test
    fun assemblesIdtPayloadsLikeNoa() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02,
            0xFF.toByte(), 0xD9.toByte(),
        )
        val packets = listOf(
            "ist:".toByteArray(Charsets.ISO_8859_1),
            "idt:".toByteArray(Charsets.ISO_8859_1) + jpeg.copyOfRange(0, 3),
            "idt:".toByteArray(Charsets.ISO_8859_1) + jpeg.copyOfRange(3, jpeg.size),
            "ien:".toByteArray(Charsets.ISO_8859_1),
        )
        assertArrayEquals(jpeg, NoaPhoto.assemble(packets))
    }

    @Test
    fun unprefixedDocsChunksStayJpeg() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0x09, 0xFF.toByte(), 0xD9.toByte(),
        )
        val packets = listOf(
            jpeg.copyOfRange(0, 2),
            jpeg.copyOfRange(2, jpeg.size),
        )
        assertArrayEquals(jpeg, NoaPhoto.assemble(packets))
    }

    @Test
    fun blobFindsSoi() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 7, 0xFF.toByte(), 0xD9.toByte())
        val blob = byteArrayOf(0x00, 0x01) + jpeg
        assertArrayEquals(jpeg, NoaPhoto.assembleBlob(blob))
        assertEquals(0, NoaPhoto.assemble(emptyList()).size)
    }
}
