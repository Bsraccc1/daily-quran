package com.quranreader.custom.data.seed

import com.quranreader.custom.data.local.ArabicVerseDao
import com.quranreader.custom.data.model.ArabicVerse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicVerseSeederTest {

    private val captured = mutableListOf<ArabicVerse>()
    private val dao = object : ArabicVerseDao {
        override suspend fun getRange(surah: Int, from: Int, to: Int) = emptyList<ArabicVerse>()
        override suspend fun getSurah(surah: Int): List<ArabicVerse> = emptyList()
        override suspend fun get(surah: Int, ayah: Int): ArabicVerse? = null
        override suspend fun count(): Int = captured.size
        override suspend fun insertAll(verses: List<ArabicVerse>) {
            captured += verses
        }
    }

    @Test fun `parses three-verse fixture`() = runTest {
        val json = """
            [
              {"s":1,"a":1,"t":"بِسْمِ"},
              {"s":1,"a":2,"t":"ٱلْحَمْدُ"},
              {"s":1,"a":3,"t":"ٱلرَّحْمَـٰنِ"}
            ]
        """.trimIndent()
        ArabicVerseSeeder(dao).seedFromString(json)
        assertEquals(3, captured.size)
        assertEquals(1 to 1, captured[0].surahNumber to captured[0].ayahNumber)
        assertEquals("بِسْمِ", captured[0].textUthmani)
        assertEquals(3, captured[2].ayahNumber)
    }

    @Test fun `skips seed when already populated`() = runTest {
        captured += ArabicVerse(1, 1, "x")
        ArabicVerseSeeder(dao).seedFromString("""[{"s":2,"a":1,"t":"y"}]""")
        // No new rows inserted because count() returned non-zero on entry.
        assertEquals(1, captured.size)
        assertEquals("x", captured[0].textUthmani)
    }
}
