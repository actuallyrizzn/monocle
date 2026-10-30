package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayCommandsTest {
    @Test
    fun showTextWrapsDisplayApi() {
        val cmd = DisplayCommands.showText("hello otto")
        assertTrue(cmd.contains("'hello otto'"))
        assertTrue(cmd.contains("def chrome():"))
        assertTrue(cmd.contains("display.show("))
        assertTrue(cmd.contains("battery_level"))
        assertTrue(cmd.contains("display.brightness(0)"))
        assertEquals(8767, DisplayCommands.HTTP_PORT)
    }

    @Test
    fun showTextEscapesQuotes() {
        val cmd = DisplayCommands.showText("it's on")
        assertTrue(cmd.contains("it\\'s on"))
    }
}
