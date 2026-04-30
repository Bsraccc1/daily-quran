package com.quranreader.custom.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies juz boundary lookup against the standard Tanzil
 * juz table. These boundaries are immutable Quranic data —
 * any change here is a bug, not a refactor.
 */
class QuranInfoJuzTest {

    @Test fun `first verse of Quran is in juz 1`() {
        assertEquals(1, QuranInfo.juzOf(1, 1))
    }

    @Test fun `last verse of Quran is in juz 30`() {
        assertEquals(30, QuranInfo.juzOf(114, 6))
    }

    @Test fun `juz 2 starts at Al-Baqarah ayah 142`() {
        assertEquals(2, QuranInfo.juzOf(2, 142))
        assertEquals(1, QuranInfo.juzOf(2, 141)) // boundary verse stays in juz 1
    }

    @Test fun `juz 14 starts at Al-Hijr ayah 1`() {
        assertEquals(14, QuranInfo.juzOf(15, 1))
        // Last verse of Ibrahim still in juz 13
        assertEquals(13, QuranInfo.juzOf(14, 52))
    }

    @Test fun `juz 30 begins at An-Naba`() {
        assertEquals(30, QuranInfo.juzOf(78, 1))
    }

    @Test fun `juz ranges expose first and last verses of juz 1`() {
        val ranges = QuranInfo.juzAyahRanges(1)
        assertEquals(2, ranges.size) // surah 1 (full) + surah 2 (1..141)
        val first = ranges.first()
        assertEquals(Triple(1, 1, 7), first) // Al-Fatihah 1..7
        val second = ranges[1]
        assertEquals(Triple(2, 1, 141), second) // Al-Baqarah 1..141
    }

    @Test fun `juz ranges are non-empty for every juz 1 to 30`() {
        for (j in 1..30) {
            val ranges = QuranInfo.juzAyahRanges(j)
            assertEquals(true, ranges.isNotEmpty())
        }
    }

    @Test fun `juz 30 ranges cover Naba through An-Nas`() {
        val ranges = QuranInfo.juzAyahRanges(30)
        assertEquals(78, ranges.first().first)  // surahNumber
        assertEquals(114, ranges.last().first)  // surahNumber
    }
}
