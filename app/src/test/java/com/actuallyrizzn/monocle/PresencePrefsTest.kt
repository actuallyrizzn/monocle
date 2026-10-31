package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresencePrefsTest {
    @Test
    fun emptyIsNotConfigured() {
        assertFalse(PresencePrefs.isConfigured("", ""))
        assertFalse(PresencePrefs.isConfigured("http://127.0.0.1:8890", ""))
        assertFalse(PresencePrefs.isConfigured("", "secret"))
        assertFalse(PresencePrefs.isConfigured("   ", "secret"))
    }

    @Test
    fun urlAndKeyAreConfigured() {
        assertTrue(PresencePrefs.isConfigured("http://127.0.0.1:8890", "secret"))
        assertEquals("http://127.0.0.1:8890", PresencePrefs.normalizeUrl("http://127.0.0.1:8890/"))
    }
}
