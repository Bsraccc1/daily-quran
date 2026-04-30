package com.quranreader.custom.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingModeTest {
    @Test fun `default is MUSHAF`() {
        assertEquals(ReadingMode.MUSHAF, ReadingMode.fromName(null))
    }

    @Test fun `unknown name falls back to MUSHAF`() {
        assertEquals(ReadingMode.MUSHAF, ReadingMode.fromName("garbage"))
    }

    @Test fun `TRANSLATION round-trips`() {
        assertEquals(ReadingMode.TRANSLATION, ReadingMode.fromName("TRANSLATION"))
    }

    @Test fun `MUSHAF round-trips`() {
        assertEquals(ReadingMode.MUSHAF, ReadingMode.fromName("MUSHAF"))
    }
}
