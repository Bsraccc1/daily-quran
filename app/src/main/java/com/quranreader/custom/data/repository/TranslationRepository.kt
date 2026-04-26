package com.quranreader.custom.data.repository

import android.content.Context
import com.quranreader.custom.data.local.TranslationDao
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.data.QuranInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    private val translationDao: TranslationDao,
    private val context: Context
) {
    suspend fun isLanguageDownloaded(lang: String): Boolean {
        return translationDao.getCountForLanguage(lang) > 0
    }

    suspend fun getTranslation(surah: Int, ayah: Int, lang: String): TranslationText? {
        return translationDao.getTranslation(surah, ayah, lang)
    }

    suspend fun getTranslationsForRange(surah: Int, fromAyah: Int, toAyah: Int, lang: String): List<TranslationText> {
        return translationDao.getTranslationsForRange(surah, fromAyah, toAyah, lang)
    }

    fun getTranslationsForSurah(surah: Int, lang: String): Flow<List<TranslationText>> {
        return translationDao.getTranslationsForSurah(surah, lang)
    }

    /**
     * Get translations for all ayahs on a specific page.
     * Uses QuranInfo to determine which surahs/ayahs are on the page.
     */
    suspend fun getTranslationsForPage(page: Int, lang: String): List<TranslationText> {
        val results = mutableListOf<TranslationText>()

        // Find surah for this page
        var currentSurah = 1
        for (s in 1..114) {
            if (QuranInfo.getStartPage(s) <= page) currentSurah = s
            else break
        }

        // Check if next surah starts on this page
        val surahsOnPage = mutableListOf(currentSurah)
        for (s in (currentSurah + 1)..114) {
            if (QuranInfo.getStartPage(s) == page) surahsOnPage.add(s)
            else if (QuranInfo.getStartPage(s) > page) break
        }

        for (surah in surahsOnPage) {
            val surahStartPage = QuranInfo.getStartPage(surah)
            val nextSurahStartPage = if (surah < 114) QuranInfo.getStartPage(surah + 1) else 605
            val totalAyahs = QuranInfo.getAyahCount(surah)
            val pagesInSurah = (nextSurahStartPage - surahStartPage).coerceAtLeast(1)

            val pageOffset = page - surahStartPage
            val ayahsPerPage = (totalAyahs.toFloat() / pagesInSurah).toInt().coerceAtLeast(1)
            val startAyah = (pageOffset * ayahsPerPage + 1).coerceIn(1, totalAyahs)
            val endAyah = if (pageOffset == pagesInSurah - 1) totalAyahs
                         else ((pageOffset + 1) * ayahsPerPage).coerceIn(startAyah, totalAyahs)

            val translations = translationDao.getTranslationsForRange(surah, startAyah, endAyah, lang)
            results.addAll(translations)
        }

        return results
    }

    /**
     * Download translation data from cdn.islamic.network API
     * API: https://api.alquran.cloud/v1/quran/{edition}
     * Editions: en.sahih (English), id.indonesian (Indonesian)
     */
    suspend fun downloadTranslation(
        lang: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val edition = when (lang) {
                "en" -> "en.sahih"
                "id" -> "id.indonesian"
                else -> return@withContext false
            }
            val translationName = when (lang) {
                "en" -> "Sahih International"
                "id" -> "Indonesian Ministry of Religious Affairs"
                else -> edition
            }

            val url = "https://api.alquran.cloud/v1/quran/$edition"
            val response = URL(url).readText()
            val json = org.json.JSONObject(response)
            val data = json.getJSONObject("data")
            val surahs = data.getJSONArray("surahs")

            val totalAyahs = 6236
            var processedAyahs = 0
            val batch = mutableListOf<TranslationText>()

            for (i in 0 until surahs.length()) {
                val surah = surahs.getJSONObject(i)
                val surahNumber = surah.getInt("number")
                val ayahs = surah.getJSONArray("ayahs")

                for (j in 0 until ayahs.length()) {
                    val ayah = ayahs.getJSONObject(j)
                    val ayahNumber = ayah.getInt("numberInSurah")
                    val text = ayah.getString("text")

                    batch.add(
                        TranslationText(
                            surahNumber = surahNumber,
                            ayahNumber = ayahNumber,
                            languageCode = lang,
                            translationName = translationName,
                            text = text
                        )
                    )

                    processedAyahs++
                    if (batch.size >= 100) {
                        translationDao.insertAll(batch)
                        batch.clear()
                        onProgress(processedAyahs, totalAyahs)
                    }
                }
            }

            if (batch.isNotEmpty()) {
                translationDao.insertAll(batch)
                onProgress(processedAyahs, totalAyahs)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getRandomAyah(lang: String): TranslationText? {
        return translationDao.getRandomAyah(lang)
    }
}
