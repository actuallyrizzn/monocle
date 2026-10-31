package com.actuallyrizzn.monocle

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudScriptTest {
    @Test
    fun residentOperatorOwnsTheDevice() {
        val src = HudScript.SOURCE
        assertTrue(src.contains("# op-v2"))
        assertTrue(src.contains("bluetooth.receive_callback"))
        assertTrue(src.contains("b'crd:'"))
        assertTrue(src.contains("b'clr:'"))
        assertTrue(src.contains("b'cam:'"))
        assertTrue(src.contains("b'mic:'"))
        assertTrue(src.contains("b'sns:'"))
        assertTrue(src.contains("b'led:'"))
        assertTrue(src.contains("b'tqs:'"))
        assertTrue(src.contains("b'ist:'"))
        assertTrue(src.contains("b'idt:'"))
        assertTrue(src.contains("b'ien:'"))
        assertTrue(src.contains("b'ast:'"))
        assertTrue(src.contains("except OSError"))
        assertTrue(src.contains("BRIGHT=0"))
        assertTrue(src.contains("def wrap("))
        assertTrue(src.contains("battery_level"))
        assertTrue(src.contains("camera.capture()"))
        assertTrue(src.contains("microphone.record"))
        assertTrue(src.contains("def wake()"))
        assertTrue(src.contains("def nap()"))
        assertTrue(src.indexOf("import camera") > src.indexOf("def do_cam()"))
        assertTrue(src.contains("finally:"))
        assertTrue(HudScript.ARM.contains("import op"))
        assertTrue(HudScript.CHECK.contains("while line==''"))
        assertTrue(HudScript.PROBE.contains("PROBE"))
        assertEquals("op-v2", HudScript.VERSION)
        assertFalse(src.trimIndent().startsWith("import camera"))
        assertFalse(HudScript.replFailed("OPEN\nWROTE 12"))
        assertTrue(HudScript.replFailed("Traceback (most recent call last):"))
        assertTrue(HudScript.replFailed("ERROR: not connected"))
    }

    @Test
    fun writePlanIsSmallPastesThatRebuildTheFile() {
        val plan = HudScript.writePlan()
        assertTrue(plan.size > 3)
        assertTrue(plan.first().contains("open('op.py'"))
        assertTrue(plan.last().startsWith("f.close()"))
        assertTrue(plan.last().contains("print('WROTE',${HudScript.sourceBytes().size})"))
        val rebuilt = java.io.ByteArrayOutputStream()
        val b64 = Regex("a2b_base64\\('([A-Za-z0-9+/=]+)'\\)")
        for (cmd in plan) {
            assertTrue("paste too large: ${cmd.length}", cmd.length < 400)
            assertFalse(cmd.contains("unhexlify"))
            val m = b64.find(cmd)
            if (m != null) {
                rebuilt.write(java.util.Base64.getDecoder().decode(m.groupValues[1]))
            }
        }
        assertArrayEquals(HudScript.sourceBytes(), rebuilt.toByteArray())
    }

    @Test
    fun cardWireFitsOnePacketForAScreen() {
        val payload = CardWire.payload("Sanctum", "Otto", "hello on the glass")
        val frames = CardWire.frames(payload)
        assertEquals(1, frames.size)
        assertEquals('c'.code.toByte(), frames[0][0])
        assertTrue(frames[0].contains(CardWire.SEP))
        assertTrue(CardWire.camera().contentEquals("cam:".toByteArray()))
        assertTrue(String(CardWire.mic(2.0), Charsets.US_ASCII).startsWith("mic:"))
    }

    @Test
    fun cardWireChunksLongBody() {
        val payload = CardWire.payload("Long", "Athena", "word ".repeat(80))
        val frames = CardWire.frames(payload, maxPacket = 40)
        assertTrue(frames.size > 1)
        assertTrue(frames.dropLast(1).all { it.copyOfRange(0, 4).contentEquals("dat:".toByteArray()) })
        assertTrue(frames.last().copyOfRange(0, 4).contentEquals("end:".toByteArray()))
    }

    @Test
    fun idleDelayIsTwentySeconds() {
        assertEquals(20_000L, IdleBlank.DELAY_MS)
    }

    @Test
    fun noaFramedAssemblesAudioDat() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val packets = listOf(
            "ast:".toByteArray(Charsets.ISO_8859_1),
            "dat:".toByteArray(Charsets.ISO_8859_1) + pcm.copyOfRange(0, 2),
            "dat:".toByteArray(Charsets.ISO_8859_1) + pcm.copyOfRange(2, 4),
            "aen:".toByteArray(Charsets.ISO_8859_1),
        )
        assertArrayEquals(pcm, NoaFramed.assemble(packets, "ast:", "dat:", "aen:"))
    }
}
