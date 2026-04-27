package com.quranreader.custom.data.local.ayahinfo

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over [GlyphDao] used by ViewModels so they don't depend
 * on the underlying Room database directly.
 */
@Singleton
class AyahInfoRepository @Inject constructor(
    private val glyphDao: GlyphDao,
) {
    suspend fun glyphsForPage(page: Int): List<GlyphEntity> =
        glyphDao.glyphsForPage(page)

    suspend fun glyphsForAyah(page: Int, surah: Int, ayah: Int): List<GlyphEntity> =
        glyphDao.glyphsForAyah(page, surah, ayah)

    /**
     * Resolve the mushaf page where (surah, ayah) starts. Used by the
     * "search by surah + ayah" flow to jump straight to the page and
     * pre-highlight the verse on arrival.
     */
    suspend fun pageForAyah(surah: Int, ayah: Int): Int? =
        glyphDao.pageForAyah(surah, ayah)
}
