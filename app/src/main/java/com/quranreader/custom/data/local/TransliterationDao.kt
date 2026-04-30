package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.Transliteration

/**
 * Cache layer for Latin-script transliterations fetched from quran.com.
 * Mirrors the API surface of [TranslationDao] — range fetch + count
 * for "have we cached this language?" checks + bulk insert from a
 * remote download.
 */
@Dao
interface TransliterationDao {
    @Query(
        "SELECT * FROM transliterations WHERE surahNumber = :surah " +
            "AND ayahNumber BETWEEN :from AND :to AND languageCode = :lang " +
            "ORDER BY ayahNumber"
    )
    suspend fun getRange(surah: Int, from: Int, to: Int, lang: String): List<Transliteration>

    @Query(
        "SELECT * FROM transliterations WHERE surahNumber = :surah AND languageCode = :lang " +
            "ORDER BY ayahNumber"
    )
    suspend fun getSurah(surah: Int, lang: String): List<Transliteration>

    @Query("SELECT COUNT(*) FROM transliterations WHERE languageCode = :lang")
    suspend fun count(lang: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<Transliteration>)
}
