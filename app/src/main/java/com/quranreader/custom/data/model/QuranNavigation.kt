package com.quranreader.custom.data.model

/**
 * Navigation models for Quran browsing
 */

data class JuzInfo(
    val number: Int,
    val startPage: Int,
    val endPage: Int
)

data class SurahInfo(
    val number: Int,
    val arabicName: String,
    val englishName: String,
    val ayahCount: Int,
    val startPage: Int,
    val isMakki: Boolean
)

data class HizbInfo(
    val number: Int,
    val quarter: Int, // 1-4 (each hizb has 4 quarters)
    val startPage: Int,
    val juzNumber: Int
)

/**
 * Tab selection for Juz Screen
 */
enum class QuranNavigationTab {
    JUZ,
    SURAH,
    HIZB
}
