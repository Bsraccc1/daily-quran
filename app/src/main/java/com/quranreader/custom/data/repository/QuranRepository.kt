package com.quranreader.custom.data.repository

import com.quranreader.custom.data.Constants
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Quran data (Surahs, Juz, Pages, etc.)
 * Now uses real data from QuranInfo (copied from quran_android)
 */
@Singleton
class QuranRepository @Inject constructor() {

    companion object {
        const val TOTAL_PAGES = Constants.PAGES_COUNT
        const val TOTAL_SURAHS = Constants.SURAS_COUNT
        const val TOTAL_JUZ = Constants.JUZ_COUNT
        const val TOTAL_HIZB = Constants.HIZB_COUNT
    }

    /**
     * Get all Surahs
     */
    fun getAllSurahs(): Flow<List<Surah>> = flow {
        emit(getSurahList())
    }

    /**
     * Get all Juz
     */
    fun getAllJuz(): Flow<List<Juz>> = flow {
        emit(getJuzList())
    }

    /**
     * Get all Hizb
     */
    fun getAllHizb(): Flow<List<Hizb>> = flow {
        emit(getHizbList())
    }

    /**
     * Get page by number
     */
    suspend fun getPage(pageNumber: Int): QuranPage? {
        if (pageNumber < 1 || pageNumber > TOTAL_PAGES) return null
        
        val surah = getSurahForPage(pageNumber)
        val juz = getJuzForPage(pageNumber)
        
        return QuranPage(
            pageNumber = pageNumber,
            surahNumber = surah.number,
            surahName = surah.name,
            juzNumber = juz.number,
            imagePath = "page_$pageNumber.png"
        )
    }

    /**
     * Get Surah by number
     */
    suspend fun getSurah(surahNumber: Int): Surah? {
        return getSurahList().find { it.number == surahNumber }
    }

    private fun getSurahForPage(page: Int): Surah {
        // Simplified - in production, read from quran_android data
        return getSurahList().lastOrNull { it.startPage <= page } ?: getSurahList().first()
    }

    private fun getJuzForPage(page: Int): Juz {
        // Simplified - in production, read from quran_android data
        return getJuzList().lastOrNull { it.startPage <= page } ?: getJuzList().first()
    }

    /**
     * Real Surah list using QuranInfo data
     */
    private fun getSurahList(): List<Surah> = (1..TOTAL_SURAHS).map { surahNum ->
        Surah(
            number = surahNum,
            name = QuranInfo.getSurahName(surahNum),
            transliteratedName = QuranInfo.getSurahEnglishName(surahNum),
            englishName = QuranInfo.getSurahEnglishName(surahNum),
            ayahCount = QuranInfo.getAyahCount(surahNum),
            startPage = QuranInfo.getStartPage(surahNum),
            isMakki = QuranInfo.isMakki(surahNum),
            revelationOrder = surahNum // Simplified - use surah number as order
        )
    }

    /**
     * Real Juz list with accurate page mapping
     */
    private fun getJuzList(): List<Juz> = listOf(
        Juz(1, 1, 21, 1, 1),
        Juz(2, 22, 41, 2, 142),
        Juz(3, 42, 61, 2, 253),
        Juz(4, 62, 81, 3, 93),
        Juz(5, 82, 101, 4, 24),
        Juz(6, 102, 121, 4, 148),
        Juz(7, 122, 141, 5, 82),
        Juz(8, 142, 161, 6, 111),
        Juz(9, 162, 181, 7, 88),
        Juz(10, 182, 201, 8, 41),
        Juz(11, 202, 221, 9, 93),
        Juz(12, 222, 241, 11, 6),
        Juz(13, 242, 261, 12, 53),
        Juz(14, 262, 281, 15, 1),
        Juz(15, 282, 301, 17, 1),
        Juz(16, 302, 321, 18, 75),
        Juz(17, 322, 341, 21, 1),
        Juz(18, 342, 361, 23, 1),
        Juz(19, 362, 381, 25, 21),
        Juz(20, 382, 401, 27, 56),
        Juz(21, 402, 421, 29, 46),
        Juz(22, 422, 441, 33, 31),
        Juz(23, 442, 461, 36, 28),
        Juz(24, 462, 481, 39, 32),
        Juz(25, 482, 501, 41, 47),
        Juz(26, 502, 521, 46, 1),
        Juz(27, 522, 541, 51, 31),
        Juz(28, 542, 561, 58, 1),
        Juz(29, 562, 581, 67, 1),
        Juz(30, 582, 604, 78, 1)
    )

    /**
     * Hardcoded Hizb list - In production, this should come from quran_android assets
     */
    private fun getHizbList(): List<Hizb> = (1..60).map { hizbNum ->
        val juzNum = ((hizbNum - 1) / 2) + 1
        val startPage = ((hizbNum - 1) * 10) + 1
        val endPage = if (hizbNum == 60) 604 else hizbNum * 10
        Hizb(
            number = hizbNum,
            juzNumber = juzNum,
            startPage = startPage,
            endPage = endPage
        )
    }
}
