package com.quranreader.custom.data.repository

import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.local.ArabicVerseDao
import com.quranreader.custom.data.model.ArabicVerse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read API for the bundled `arabic_verses` table. Pure DB reads — no
 * network. The seeder ([com.quranreader.custom.data.seed.ArabicVerseSeeder])
 * populates the table once on first launch.
 *
 * This repository deliberately does NOT expose a [Flow]; the
 * `arabic_verses` table is write-once, so a single suspend fetch per
 * juz is enough.
 */
@Singleton
class ArabicVerseRepository @Inject constructor(
    private val dao: ArabicVerseDao,
) {

    /** Verses for a `(surah, fromAyah..toAyah)` slice. */
    suspend fun getRange(surah: Int, fromAyah: Int, toAyah: Int): List<ArabicVerse> =
        dao.getRange(surah, fromAyah, toAyah)

    /** All verses for a single surah. */
    suspend fun getSurah(surah: Int): List<ArabicVerse> = dao.getSurah(surah)

    /**
     * All verses in a juz, flattened in reading order. The juz is split
     * into surah-bounded slices via [QuranInfo.juzAyahRanges] and each
     * slice is queried independently — Room DAO + small fetch sizes
     * make this efficient even for the larger juz (juz 1 has ~148 verses).
     */
    suspend fun getJuz(juz: Int): List<ArabicVerse> {
        val out = ArrayList<ArabicVerse>(160)
        for ((surah, from, to) in QuranInfo.juzAyahRanges(juz)) {
            out += dao.getRange(surah, from, to)
        }
        return out
    }
}
