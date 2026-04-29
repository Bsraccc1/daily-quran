package com.quranreader.custom.data.repository

import android.content.Context
import android.util.Log
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.local.AyahCoordinateDao
import com.quranreader.custom.data.local.TranslationDao
import com.quranreader.custom.data.local.TranslationEditionDao
import com.quranreader.custom.data.local.ayahinfo.AyahInfoRepository
import com.quranreader.custom.data.model.TranslationEdition
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.data.remote.QuranComApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translation data layer.
 *
 *  - Catalogue (which editions exist) comes from
 *    [QuranComApi.listTranslations] and is mirrored into the
 *    `translation_editions` Room table so the picker is offline-readable
 *    after the first refresh.
 *  - Verses come from [QuranComApi.fetchTranslation] and are written into
 *    the existing `translations` table keyed by `editionId` (quran.com
 *    `translation_id`). Multiple editions can coexist for the same
 *    language — picking "Sahih" doesn't blow away "Pickthall".
 *  - Per-page reads use the bundled `ayahinfo.db` glyph table to derive
 *    exactly which (surah, ayah) pairs sit on the page, so the
 *    translation panel only ever shows verses that actually appear on
 *    the page the user is looking at.
 */
@Singleton
class TranslationRepository @Inject constructor(
    private val translationDao: TranslationDao,
    private val editionDao: TranslationEditionDao,
    /**
     * Legacy per-page (surah, ayah) source. The runtime never
     * populates the underlying `ayah_coordinates` table, so this is
     * only kept as a defensive fallback in case the bundled
     * `ayahinfo.db` ever fails to load. The primary source is
     * [ayahInfoRepository] below.
     */
    private val ayahCoordinateDao: AyahCoordinateDao,
    /**
     * Per-page glyph table from the bundled, pre-packaged
     * `ayahinfo.db`. This is the same data the mushaf renderer uses
     * for tap-to-highlight, which means the translation panel and
     * the mushaf agree on "which verses are on this page" — critical
     * for HIGHLIGHTED_ONLY mode (otherwise the user can highlight a
     * verse that isn't in the translation list and the panel falls
     * to "No translation available" even though the edition has it).
     */
    private val ayahInfoRepository: AyahInfoRepository,
    private val quranComApi: QuranComApi,
    private val context: Context
) {
    // ── Edition catalogue ────────────────────────────────────────────────────

    /**
     * Cached list of editions, observable so the picker re-renders
     * automatically after [refreshEditionCatalogue] writes back.
     */
    fun observeEditions(): Flow<List<TranslationEdition>> = editionDao.observeAll()

    fun observeInstalledEditions(): Flow<List<TranslationEdition>> = editionDao.observeInstalled()

    /**
     * Pull the latest editions from quran.com, merge with installed
     * status from Room, and persist back. Network-only — caller should
     * surface failure in the UI.
     */
    suspend fun refreshEditionCatalogue(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = quranComApi.listTranslations()
            val installedById = editionDao.listAll().associateBy { it.editionId }
            val merged = response.translations.map { dto ->
                val existing = installedById[dto.id]
                TranslationEdition(
                    editionId = dto.id,
                    name = dto.name,
                    authorName = dto.authorName,
                    languageName = dto.languageName,
                    slug = dto.slug,
                    isDownloaded = existing?.isDownloaded ?: false,
                    verseCount = existing?.verseCount ?: 0,
                    lastDownloadedAt = existing?.lastDownloadedAt ?: 0L,
                )
            }
            if (merged.isNotEmpty()) editionDao.upsertAll(merged)
            true
        } catch (e: Exception) {
            Log.w(TAG, "refreshEditionCatalogue failed", e)
            false
        }
    }

    suspend fun getEdition(editionId: Int): TranslationEdition? = editionDao.getById(editionId)

    // ── Reads (edition-aware) ────────────────────────────────────────────────

    suspend fun isEditionInstalled(editionId: Int): Boolean {
        return translationDao.getCountForEdition(editionId) > 0
    }

    suspend fun getTranslation(surah: Int, ayah: Int, editionId: Int): TranslationText? {
        return translationDao.getByEdition(surah, ayah, editionId)
    }

    /**
     * Translations for every (surah, ayah) actually present on [page],
     * derived from `ayahinfo.db`. Falls back to the legacy estimation
     * algorithm when ayahinfo has no rows (e.g. corrupted asset DB)
     * so the panel still renders something instead of going blank.
     */
    suspend fun getTranslationsForPage(page: Int, editionId: Int): List<TranslationText> {
        val ayahsOnPage = ayahsOnPage(page)
        if (ayahsOnPage.isEmpty()) return emptyList()
        // Group consecutive ayahs by surah so we hit the DAO once per
        // surah-on-page (typically one or two surahs per page).
        val resultsBySurah = ayahsOnPage
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ayahs) -> ayahs.min() to ayahs.max() }

        val out = mutableListOf<TranslationText>()
        for ((surah, range) in resultsBySurah) {
            val (from, to) = range
            out.addAll(translationDao.getRangeByEdition(surah, from, to, editionId))
        }
        return out.sortedWith(compareBy({ it.surahNumber }, { it.ayahNumber }))
    }

    /**
     * Resolve the (surah, ayah) pairs that actually appear on the
     * given mushaf [page]. Three-tier lookup:
     *
     *   1. **`ayahinfo.db` glyph table** — the bundled, ground-truth
     *      per-glyph rectangle table that powers tap-to-highlight.
     *      This is the same data the mushaf uses, which guarantees
     *      the translation panel agrees with what the user sees.
     *   2. **Legacy `ayah_coordinates` table** — only if the glyph
     *      table happens to be empty (corrupted asset DB). The
     *      runtime never populates this table at runtime; the path
     *      exists purely as a defence-in-depth.
     *   3. **Coarse estimate** — last-resort "average ayahs per
     *      page within a surah" fallback so the panel still renders
     *      something instead of going blank when both DBs fail.
     */
    private suspend fun ayahsOnPage(page: Int): List<Pair<Int, Int>> {
        val glyphs = try {
            ayahInfoRepository.glyphsForPage(page)
        } catch (e: Exception) {
            Log.w("TranslationRepository", "glyphsForPage($page) failed", e)
            emptyList()
        }
        if (glyphs.isNotEmpty()) {
            return glyphs.map { it.suraNumber to it.ayahNumber }.distinct()
        }
        val coords = try {
            ayahCoordinateDao.getCoordinatesForPage(page)
        } catch (e: Exception) {
            emptyList()
        }
        if (coords.isNotEmpty()) {
            return coords.map { it.surah to it.ayah }.distinct()
        }
        // Coarse fallback — average ayahs/page within a surah.
        val pairs = mutableListOf<Pair<Int, Int>>()
        var currentSurah = 1
        for (s in 1..114) {
            if (QuranInfo.getStartPage(s) <= page) currentSurah = s else break
        }
        val surahsOnPage = mutableListOf(currentSurah)
        for (s in (currentSurah + 1)..114) {
            if (QuranInfo.getStartPage(s) == page) surahsOnPage.add(s) else break
        }
        for (surah in surahsOnPage) {
            val surahStart = QuranInfo.getStartPage(surah)
            val nextStart = if (surah < 114) QuranInfo.getStartPage(surah + 1) else 605
            val total = QuranInfo.getAyahCount(surah)
            val span = (nextStart - surahStart).coerceAtLeast(1)
            val perPage = (total.toFloat() / span).toInt().coerceAtLeast(1)
            val offset = page - surahStart
            val from = (offset * perPage + 1).coerceIn(1, total)
            val to = if (offset == span - 1) total else ((offset + 1) * perPage).coerceIn(from, total)
            for (a in from..to) pairs.add(surah to a)
        }
        return pairs
    }

    // ── Downloads ────────────────────────────────────────────────────────────

    /**
     * Stream every verse of [editionId] from quran.com into Room. Idempotent —
     * re-running overwrites existing rows (`onConflict = REPLACE`) so a
     * partial previous attempt is harmless. Reports `(downloaded,
     * total)` periodically; total is fixed at 6,236 so the progress
     * bar never spikes when the API returns slightly fewer rows.
     */
    suspend fun downloadEdition(
        editionId: Int,
        languageCode: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = quranComApi.fetchTranslation(editionId)
            val editionMeta = editionDao.getById(editionId)
                ?: TranslationEdition(
                    editionId = editionId,
                    name = response.meta?.translationName ?: "Edition $editionId",
                    authorName = response.meta?.authorName,
                    languageName = languageCode,
                )
            val translationName = editionMeta.name

            val totalAyahs = 6236
            val batch = mutableListOf<TranslationText>()
            var processed = 0
            for (verse in response.translations) {
                val pair = verse.parseSurahAyah() ?: continue
                batch.add(
                    TranslationText(
                        surahNumber = pair.first,
                        ayahNumber = pair.second,
                        editionId = editionId,
                        languageCode = languageCode,
                        translationName = translationName,
                        text = verse.text.stripBasicHtml(),
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

            // Persist installed status so the picker re-renders.
            editionDao.upsertAll(
                listOf(
                    editionMeta.copy(
                        isDownloaded = processed > 0,
                        verseCount = processed,
                        lastDownloadedAt = System.currentTimeMillis(),
                    )
                )
            )
            processed > 0
        } catch (e: Exception) {
            Log.w(TAG, "downloadEdition($editionId) failed", e)
            false
        }
    }

    /**
     * Delete every verse for [editionId] and flag the edition as
     * uninstalled. Catalogue row stays so the user can re-download
     * later without another refresh.
     */
    suspend fun deleteEdition(editionId: Int) = withContext(Dispatchers.IO) {
        translationDao.deleteEdition(editionId)
        val current = editionDao.getById(editionId) ?: return@withContext
        editionDao.upsertAll(listOf(current.copy(isDownloaded = false, verseCount = 0)))
    }

    // ── Random / daily-verse (legacy by language) ────────────────────────────

    suspend fun getRandomAyahForEdition(editionId: Int): TranslationText? =
        translationDao.getRandomAyahForEdition(editionId)

    suspend fun getRandomAyah(lang: String): TranslationText? =
        translationDao.getRandomAyah(lang)

    /**
     * quran.com returns translation text with a few markup
     * artefacts (footnote `<sup>` tags, `<i>` italics). We strip the
     * tags but keep the inner text — fancy rendering can come later.
     */
    private fun String.stripBasicHtml(): String = replace(HTML_TAG_REGEX, "").trim()

    companion object {
        private const val TAG = "TranslationRepo"
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
    }
}
