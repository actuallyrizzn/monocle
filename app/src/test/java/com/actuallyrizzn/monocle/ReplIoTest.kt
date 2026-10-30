package com.actuallyrizzn.monocle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplIoTest {
    @Test
    fun promptAtEnd() {
        assertTrue(ReplIo.atPrompt(">>>"))
        assertTrue(ReplIo.atPrompt("hello\r\n>>>"))
        assertTrue(ReplIo.atPrompt("hello\r\n>>> \r\n"))
    }

    @Test
    fun notPromptYet() {
        assertFalse(ReplIo.atPrompt(""))
        assertFalse(ReplIo.atPrompt("print(1)\r\n"))
        assertFalse(ReplIo.atPrompt("Traceback (most recent call last):"))
    }

    @Test
    fun rawReady() {
        assertTrue(ReplIo.rawReady("raw REPL; CTRL-B to exit\r\n>"))
        assertTrue(ReplIo.rawReady("OK\u0004\u0004>"))
        assertFalse(ReplIo.rawReady("print(1)"))
    }
}
