package com.quranreader.custom.data.repository

import android.util.Log
import com.quranreader.custom.data.local.TransliterationDao
import com.quranreader.custom.data.model.Transliteration
import com.quranreader.custom.data.remote.QuranComApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Latin-script transliteration data layer. Mirrors
 * [TranslationRepository] in shape — Room table is the durable cache,
 * quran.com is the source of truth.
 *
 * quran.com's API treats transliterations as a special kind of
 * "translation" — each is registered with its own `translation_id` in
 * the editions catalogue. We fetch via the existing
 * [QuranComApi.fetchTranslation] endpoint (no new endpoint needed)
 * and persist into our `transliterations` table keyed by
 * `(surah, ayah, languageCode)`.
 *
 * **Default English transliteration**: quran.com `translation_id = 57`
 * (Transliteration by Sahih International). Customise via
 * [download].
 */
@Singleton
class TransliterationRepository @Inject constructor(
    private val dao: TransliterationDao,
    private val quranComApi: QuranComApi,
) {

    /** Cached rows for `(surah, fromAyah..toAyah)` in [language]. */
    suspend fun getRange(
        surah: Int,
        fromAyah: Int,
        toAyah: Int,
        language: String = DEFAULT_LANGUAGE,
    ): List<Transliteration> = dao.getRange(surah, fromAyah, toAyah, language)

    /** True iff at least one transliteration row is cached for [language]. */
    suspend fun isCached(language: String = DEFAULT_LANGUAGE): Boolean =
        dao.count(language) > 0

    /**
     * Pull every verse for the given quran.com translation_id and
     * cache locally. Idempotent — re-runs replace existing rows
     * (`OnConflictStrategy.REPLACE`).
     *
     * Caller must dispatch this — wraps `withContext(Dispatchers.IO)`
     * but is safe to call from any context.
     */
    suspend fun download(
        translationId: Int = DEFAULT_TRANSLITERATION_ID,
        language: String = DEFAULT_LANGUAGE,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = quranComApi.fetchTranslation(translationId)
            if (response.translations.isEmpty()) return@withContext false
            val rows = response.translations.mapNotNull { dto ->
                val (surah, ayah) = dto.parseSurahAyah() ?: return@mapNotNull null
                Transliteration(
                    surahNumber = surah,
                    ayahNumber = ayah,
                    languageCode = language,
                    text = dto.text,
                )
            }
            // Bulk insert — DAO already handles batching at the SQLite
            // layer for inserts under a few thousand rows.
            dao.insertAll(rows)
            true
        } catch (e: Exception) {
            Log.w(TAG, "download($translationId) failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "TransliterationRepo"

        /** quran.com `translation_id` for the default English transliteration. */
        const val DEFAULT_TRANSLITERATION_ID = 57

        /** Default language code stored in the cache. */
        const val DEFAULT_LANGUAGE = "en"
    }
}
