package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensTextTest {
    @Test
    fun wrapsWordsToCols() {
        val lines = LensText.wrap(
            "This is Otto. The lights on the front of the clip face out, so you will not see them.",
            cols = 24,
            maxLines = 7,
        )
        assertTrue(lines.size in 3..7)
        assertTrue(lines.all { it.length <= 24 })
        assertTrue(lines.first().startsWith("This is Otto"))
    }

    @Test
    fun splitsOverlongWord() {
        val lines = LensText.wrap("abcdefghijklmnopqrstuvwxyz0123", cols = 10, maxLines = 5)
        assertEquals("abcdefghij", lines[0])
        assertEquals("klmnopqrst", lines[1])
    }

    @Test
    fun keepsLinesPastOneScreen() {
        val text = "word ".repeat(80)
        val lines = LensText.wrap(text, cols = 24, maxLines = 48)
        assertTrue(lines.size > 7)
    }

    @Test
    fun subjectLeavesRoomForBattery() {
        assertEquals(21, LensText.SUBJ_COLS)
        val clipped = LensText.clip("abcdefghijklmnopqrstuvwxyz", LensText.SUBJ_COLS)
        assertEquals(21, clipped.length)
        val subjectRight = clipped.length * 24
        val batteryLeft = 640 - LensText.BAT_COLS * 24
        assertTrue(subjectRight < batteryLeft)
        assertEquals(100, LensText.BODY_Y)
        assertEquals(8, LensText.ROWS)
    }
}
