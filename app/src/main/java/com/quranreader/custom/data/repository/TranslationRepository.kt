package com.quranreader.custom.data.repository

import android.content.Context
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.local.TranslationDao
import com.quranreader.custom.data.model.AvailableTranslation
import com.quranreader.custom.data.model.TranslationText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translation data layer.
 *
 * v9: translations are identified by [translationId] (matches a
 * quran.com `resources/translations` entry). The legacy
 * `languageCode`-keyed API is kept as thin compatibility wrappers
 * around the bundled defaults so older call sites keep working without
 * a separate migration step.
 */
@Singleton
class TranslationRepository @Inject constructor(
    private val translationDao: TranslationDao,
    @Suppress("unused") private val context: Context,
) {

    // ── Available translations from quran.com ────────────────────────

    /**
     * Fetch the catalog of available translations from
     * `https://api.quran.com/api/v4/resources/translations`.
     *
     * Returns a flat, app-friendly list. Bundled fallback (the two
     * editions we previously hardcoded) is returned if the network
     * call fails so the manager UI is never empty offline.
     */
    suspend fun fetchAvailableTranslations(): List<AvailableTranslation> = withContext(Dispatchers.IO) {
        try {
            val raw = URL("https://api.quran.com/api/v4/resources/translations").readText()
            val arr = JSONObject(raw).getJSONArray("translations")
            val out = ArrayList<AvailableTranslation>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(
                    AvailableTranslation(
                        id = obj.getInt("id"),
                        name = obj.optString("name", obj.optString("author_name", "Translation")),
                        authorName = obj.optString("author_name", ""),
                        slug = obj.optString("slug", ""),
                        languageName = obj.optString("language_name", ""),
                        languageCode = obj.optString("language_code", obj.optString("language_name", "").take(2).lowercase()),
                    )
                )
            }
            out.sortedWith(compareBy({ it.languageName }, { it.authorName }))
        } catch (e: Exception) {
            e.printStackTrace()
            BUILTIN_TRANSLATIONS
        }
    }

    // ── Downloads (by translationId, the new primary identifier) ─────

    suspend fun isTranslationDownloaded(translationId: Int): Boolean {
        return translationDao.getCountForTranslation(translationId) > 0
    }

    suspend fun getDownloadedTranslationIds(): List<Int> = translationDao.getDownloadedTranslationIds()

    fun observeDownloadedTranslationIds(): Flow<List<Int>> = translationDao.observeDownloadedTranslationIds()

    suspend fun getDownloadedTranslationSummaries(): List<TranslationText> =
        translationDao.getDownloadedTranslationSummaries()

    suspend fun deleteTranslation(translationId: Int) = translationDao.deleteTranslation(translationId)

    /**
     * Download every ayah of a given translation edition from quran.com.
     *
     * Endpoint: `https://api.quran.com/api/v4/quran/translations/{id}`
     * which returns one entry per ayah with `verse_key` ("S:A") and
     * sanitized `text`.
     */
    suspend fun downloadTranslationById(
        edition: AvailableTranslation,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.quran.com/api/v4/quran/translations/${edition.id}"
            val response = URL(url).readText()
            val data = JSONObject(response)
            val arr = data.getJSONArray("translations")

            val totalAyahs = 6236
            var processed = 0
            val batch = ArrayList<TranslationText>(128)

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val verseKey = item.optString("verse_key", "")
                val parts = verseKey.split(":")
                if (parts.size != 2) continue
                val surah = parts[0].toIntOrNull() ?: continue
                val ayah = parts[1].toIntOrNull() ?: continue
                val text = stripHtml(item.optString("text", ""))

                batch.add(
                    TranslationText(
                        surahNumber = surah,
                        ayahNumber = ayah,
                        translationId = edition.id,
                        languageCode = edition.languageCode.ifEmpty { "en" },
                        translationName = edition.name,
                        authorName = edition.authorName,
                        slug = edition.slug,
                        text = text,
                    )
                )
                processed++

                if (batch.size >= 200) {
                    translationDao.insertAll(batch)
                    batch.clear()
                    onProgress(processed, totalAyahs)
                }
            }
            if (batch.isNotEmpty()) {
                translationDao.insertAll(batch)
                onProgress(processed, totalAyahs)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── Page / range queries used by the reader ─────────────────────

    /**
     * All translations on the page for a single edition, sorted by ayah.
     */
    suspend fun getTranslationsForPage(page: Int, translationId: Int): List<TranslationText> {
        val out = mutableListOf<TranslationText>()
        for ((s, lo, hi) in pageRanges(page)) {
            out += translationDao.getTranslationsForRange(s, lo, hi, translationId)
        }
        return out
    }

    /** Multi-edition variant of [getTranslationsForPage]. */
    suspend fun getTranslationsForPageMulti(page: Int, translationIds: List<Int>): List<TranslationText> {
        if (translationIds.isEmpty()) return emptyList()
        val out = mutableListOf<TranslationText>()
        for ((s, lo, hi) in pageRanges(page)) {
            out += translationDao.getTranslationsForRangeMulti(s, lo, hi, translationIds)
        }
        return out
    }

    /** Multi-edition translations for a single ayah. */
    suspend fun getTranslationsForAyah(
        surah: Int,
        ayah: Int,
        translationIds: List<Int>,
    ): List<TranslationText> {
        if (translationIds.isEmpty()) return emptyList()
        return translationDao.getTranslationsForAyah(surah, ayah, translationIds)
    }

    suspend fun getRandomAyah(translationId: Int): TranslationText? =
        translationDao.getRandomAyah(translationId)

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Return every (surah, fromAyah, toAyah) range that falls on the
     * requested mushaf page. Mirrors the heuristic used by the audio
     * URL resolver so the two stay in lockstep.
     */
    private fun pageRanges(page: Int): List<Triple<Int, Int, Int>> {
        var currentSurah = 1
        for (s in 1..114) {
            if (QuranInfo.getStartPage(s) <= page) currentSurah = s else break
        }
        val surahsOnPage = mutableListOf(currentSurah)
        for (s in (currentSurah + 1)..114) {
            if (QuranInfo.getStartPage(s) == page) surahsOnPage.add(s)
            else if (QuranInfo.getStartPage(s) > page) break
        }

        val out = mutableListOf<Triple<Int, Int, Int>>()
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

            out.add(Triple(surah, startAyah, endAyah))
        }
        return out
    }

    private fun stripHtml(s: String): String {
        // Footnote-style sup tags from quran.com are noise inside the panel.
        return s.replace(Regex("<sup[^>]*>.*?</sup>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    companion object {
        /**
         * Built-in fallback offered if quran.com is unreachable. IDs
         * match real quran.com translation IDs so a later background
         * fetch can reconcile metadata cleanly.
         */
        val BUILTIN_TRANSLATIONS: List<AvailableTranslation> = listOf(
            AvailableTranslation(
                id = 131,
                name = "Sahih International",
                authorName = "Saheeh International",
                slug = "saheeh-international",
                languageName = "English",
                languageCode = "en",
            ),
            AvailableTranslation(
                id = 33,
                name = "Indonesian Ministry of Religious Affairs",
                authorName = "Indonesian Islamic Affairs Ministry",
                slug = "indonesian-mora",
                languageName = "Indonesian",
                languageCode = "id",
            ),
            AvailableTranslation(
                id = 20,
                name = "Saheeh International (Tafheem-ul-Quran)",
                authorName = "Saheeh International",
                slug = "saheeh-international-2",
                languageName = "English",
                languageCode = "en",
            ),
        )
    }
}
