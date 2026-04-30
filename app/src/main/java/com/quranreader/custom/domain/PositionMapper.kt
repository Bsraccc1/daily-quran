package com.quranreader.custom.domain

import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.local.ayahinfo.AyahInfoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates between the two readers' coordinate spaces:
 *  - **Mushaf reader** speaks page numbers (1..604).
 *  - **Translation reader** speaks `(surah, ayah)` pairs.
 *
 * Backed by the bundled `ayahinfo.db` (the same source the Mushaf
 * highlighter uses), so the two readers always agree on which verse
 * sits where.
 *
 * Pure-business logic, no Android imports — usable from `:app`'s
 * lower data layer or directly from a ViewModel without bringing in
 * the framework.
 */
@Singleton
class PositionMapper @Inject constructor(
    private val ayahInfo: AyahInfoRepository,
) {

    /**
     * `(surah, ayah)` of the first ayah on [page]. Returns `null` if
     * `ayahinfo.db` returned no glyphs (corrupted asset DB) — caller
     * should treat that as "stay where the user was".
     */
    suspend fun pageToSurahAyah(page: Int): SurahAyah? {
        val glyphs = ayahInfo.glyphsForPage(page)
        if (glyphs.isEmpty()) return null
        // Glyphs are returned ordered by (line_number, position) per
        // the DAO query, so the first row is the first character on
        // the page — and therefore in the first ayah on the page.
        val first = glyphs.first()
        return SurahAyah(first.suraNumber, first.ayahNumber)
    }

    /**
     * The mushaf page that contains [surah]:[ayah]. Falls back to
     * [QuranInfo.getStartPage] if the bundled glyph table doesn't have
     * the verse (e.g. asset DB stripped on a future build) — every
     * surah's start page is hardcoded in [QuranInfo] so we always
     * return *some* page rather than crash.
     */
    suspend fun surahAyahToPage(surah: Int, ayah: Int): Int {
        return ayahInfo.pageForAyah(surah, ayah) ?: QuranInfo.getStartPage(surah)
    }

    /** Convenience wrapper for [QuranInfo.juzOf]. */
    fun surahAyahToJuz(surah: Int, ayah: Int): Int = QuranInfo.juzOf(surah, ayah)
}
