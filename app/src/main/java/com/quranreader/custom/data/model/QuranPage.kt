package com.quranreader.custom.data.model

/**
 * Represents a single page of the Quran
 */
data class QuranPage(
    val pageNumber: Int,
    val surahNumber: Int,
    val surahName: String,
    val juzNumber: Int,
    val imagePath: String
)

/**
 * Represents a Surah (chapter) of the Quran
 */
data class Surah(
    val number: Int,
    val name: String,
    val transliteratedName: String,
    val englishName: String,
    val ayahCount: Int,
    val startPage: Int,
    val isMakki: Boolean,
    val revelationOrder: Int
)

/**
 * Represents a Juz (part) of the Quran
 */
data class Juz(
    val number: Int,
    val startPage: Int,
    val endPage: Int,
    val startSurah: Int,
    val startAyah: Int
)

/**
 * Represents a Hizb (half of a Juz)
 */
data class Hizb(
    val number: Int,
    val juzNumber: Int,
    val startPage: Int,
    val endPage: Int
)

/**
 * Represents an Ayah (verse) location
 */
data class AyahLocation(
    val surah: Int,
    val ayah: Int,
    val page: Int
)
