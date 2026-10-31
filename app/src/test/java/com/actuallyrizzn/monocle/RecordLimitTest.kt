package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordLimitTest {
    @Test
    fun defaultIsFour() {
        assertEquals(4.0, RecordLimit.DEFAULT, 0.0)
    }

    @Test
    fun clampHoldsTheFence() {
        assertEquals(1.0, RecordLimit.clamp(0.2), 0.0)
        assertEquals(8.0, RecordLimit.clamp(30.0), 0.0)
        assertEquals(4.0, RecordLimit.clamp(Double.NaN), 0.0)
        assertEquals(3.5, RecordLimit.clamp(3.5), 0.0)
    }

    @Test
    fun applyNeverExceedsTheAppCap() {
        assertEquals(4.0, RecordLimit.apply(null, 4.0), 0.0)
        assertEquals(2.0, RecordLimit.apply(2.0, 4.0), 0.0)
        assertEquals(4.0, RecordLimit.apply(8.0, 4.0), 0.0)
        assertEquals(8.0, RecordLimit.apply(99.0, 12.0), 0.0)
        assertEquals(1.0, RecordLimit.apply(0.1, 8.0), 0.0)
    }
}
