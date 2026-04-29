package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.TranslationText
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    // ── Single-edition lookups (by translationId) ─────────────────────

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber = :ayah AND translationId = :translationId LIMIT 1")
    suspend fun getTranslation(surah: Int, ayah: Int, translationId: Int): TranslationText?

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND translationId = :translationId ORDER BY ayahNumber")
    fun getTranslationsForSurah(surah: Int, translationId: Int): Flow<List<TranslationText>>

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber BETWEEN :fromAyah AND :toAyah AND translationId = :translationId ORDER BY ayahNumber")
    suspend fun getTranslationsForRange(surah: Int, fromAyah: Int, toAyah: Int, translationId: Int): List<TranslationText>

    @Query("SELECT COUNT(*) FROM translations WHERE translationId = :translationId")
    suspend fun getCountForTranslation(translationId: Int): Int

    @Query("DELETE FROM translations WHERE translationId = :translationId")
    suspend fun deleteTranslation(translationId: Int)

    @Query("SELECT * FROM translations WHERE translationId = :translationId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAyah(translationId: Int): TranslationText?

    // ── Multi-edition queries (used by the side panel) ────────────────

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber = :ayah AND translationId IN (:translationIds) ORDER BY translationId")
    suspend fun getTranslationsForAyah(surah: Int, ayah: Int, translationIds: List<Int>): List<TranslationText>

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber BETWEEN :fromAyah AND :toAyah AND translationId IN (:translationIds) ORDER BY ayahNumber, translationId")
    suspend fun getTranslationsForRangeMulti(surah: Int, fromAyah: Int, toAyah: Int, translationIds: List<Int>): List<TranslationText>

    // ── Manager queries ──────────────────────────────────────────────

    /**
     * Distinct translations the user has actually downloaded.
     * Returns one row per translationId, with the metadata of the
     * first ayah in that edition.
     */
    @Query("""
        SELECT t.* FROM translations t
        INNER JOIN (
            SELECT translationId, MIN(id) AS minId
            FROM translations
            GROUP BY translationId
        ) g ON t.id = g.minId
        ORDER BY t.languageCode, t.translationName
    """)
    suspend fun getDownloadedTranslationSummaries(): List<TranslationText>

    @Query("SELECT DISTINCT translationId FROM translations")
    suspend fun getDownloadedTranslationIds(): List<Int>

    @Query("SELECT DISTINCT translationId FROM translations")
    fun observeDownloadedTranslationIds(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(translations: List<TranslationText>)
}
