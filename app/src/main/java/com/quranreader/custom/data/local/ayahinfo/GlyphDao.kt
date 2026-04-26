package com.quranreader.custom.data.local.ayahinfo

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only DAO over the bundled `ayahinfo.db`. The runtime never
 * writes to this database — it ships with the APK and is used only
 * for tap-to-highlight and audio-driven highlighting.
 */
@Dao
interface GlyphDao {

    /** All glyphs that belong to the given mushaf page. */
    @Query("SELECT * FROM glyphs WHERE page_number = :page ORDER BY line_number, position")
    suspend fun glyphsForPage(page: Int): List<GlyphEntity>

    /** Glyphs for a single ayah on a single page (used to draw highlights). */
    @Query("""
        SELECT * FROM glyphs
        WHERE page_number = :page AND sura_number = :surah AND ayah_number = :ayah
        ORDER BY line_number, position
    """)
    suspend fun glyphsForAyah(page: Int, surah: Int, ayah: Int): List<GlyphEntity>

    @Query("SELECT COUNT(*) FROM glyphs")
    suspend fun count(): Int
}
