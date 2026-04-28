package com.quranreader.custom.data.local

import androidx.room.*
import com.quranreader.custom.data.model.TranslationText
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    // ── Edition-aware queries (v9+) ──────────────────────────────────────────

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber = :ayah AND editionId = :editionId LIMIT 1")
    suspend fun getByEdition(surah: Int, ayah: Int, editionId: Int): TranslationText?

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber BETWEEN :fromAyah AND :toAyah AND editionId = :editionId ORDER BY ayahNumber")
    suspend fun getRangeByEdition(surah: Int, fromAyah: Int, toAyah: Int, editionId: Int): List<TranslationText>

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND editionId = :editionId ORDER BY ayahNumber")
    fun getSurahByEdition(surah: Int, editionId: Int): Flow<List<TranslationText>>

    @Query("SELECT COUNT(*) FROM translations WHERE editionId = :editionId")
    suspend fun getCountForEdition(editionId: Int): Int

    @Query("DELETE FROM translations WHERE editionId = :editionId")
    suspend fun deleteEdition(editionId: Int)

    @Query("SELECT * FROM translations WHERE editionId = :editionId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAyahForEdition(editionId: Int): TranslationText?

    // ── Legacy language-based queries (kept for widgets / daily-verse) ───────

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber = :ayah AND languageCode = :lang LIMIT 1")
    suspend fun getTranslation(surah: Int, ayah: Int, lang: String): TranslationText?

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND languageCode = :lang ORDER BY ayahNumber")
    fun getTranslationsForSurah(surah: Int, lang: String): Flow<List<TranslationText>>

    @Query("SELECT * FROM translations WHERE surahNumber = :surah AND ayahNumber BETWEEN :fromAyah AND :toAyah AND languageCode = :lang ORDER BY ayahNumber")
    suspend fun getTranslationsForRange(surah: Int, fromAyah: Int, toAyah: Int, lang: String): List<TranslationText>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(translations: List<TranslationText>)

    @Query("SELECT COUNT(*) FROM translations WHERE languageCode = :lang")
    suspend fun getCountForLanguage(lang: String): Int

    @Query("DELETE FROM translations WHERE languageCode = :lang")
    suspend fun deleteLanguage(lang: String)

    @Query("SELECT * FROM translations WHERE languageCode = :lang ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAyah(lang: String): TranslationText?
}
