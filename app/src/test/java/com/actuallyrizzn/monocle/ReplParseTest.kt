package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplParseTest {
    @Test
    fun labeledFromRawRepl() {
        val repl = "OKBAT 47\r\nCHG False\r\nVER v23.248.0754\u0004\u0004>"
        val map = ReplParse.labeled(repl)
        assertEquals("47", map["BAT"])
        assertEquals("False", map["CHG"])
        assertEquals("v23.248.0754", map["VER"])
    }

    @Test
    fun hexPayload() {
        val bytes = ReplParse.hexPayload("OKHEX 4869\u0004\u0004>")
        assertTrue(bytes != null)
        assertEquals("Hi", bytes!!.toString(Charsets.US_ASCII))
    }
}
