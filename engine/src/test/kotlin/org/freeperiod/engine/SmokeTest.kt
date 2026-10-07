package org.freeperiod.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun engineCompiles() {
        assertEquals("FreePeriod.", Engine.NAME)
    }
}
