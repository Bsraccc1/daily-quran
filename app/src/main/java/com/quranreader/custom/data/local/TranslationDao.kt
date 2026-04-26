package com.quranreader.custom.data.local

import androidx.room.*
import com.quranreader.custom.data.model.TranslationText
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
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
