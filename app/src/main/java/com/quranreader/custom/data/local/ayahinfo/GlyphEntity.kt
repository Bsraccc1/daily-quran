package com.quranreader.custom.data.local.ayahinfo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per glyph in the bundled `ayahinfo.db`. A glyph is a small
 * piece of an Arabic word — one ayah usually spans dozens of glyphs
 * across one or several lines.
 *
 * Coordinates are in the **page-image pixel space** of the bundled
 * 1024×1656 WebP pages. The runtime renderer scales them to the
 * displayed page size before doing tap-hit testing or drawing
 * highlight rectangles.
 *
 * The two indices (`page_idx`, `sura_ayah_idx`) match the ones the
 * build script creates inside the bundled DB so Room's pre-packaged
 * schema validation passes.
 */
@Entity(
    tableName = "glyphs",
    indices = [
        Index(name = "page_idx", value = ["page_number"]),
        Index(name = "sura_ayah_idx", value = ["sura_number", "ayah_number"]),
    ],
)
data class GlyphEntity(
    @PrimaryKey @ColumnInfo(name = "glyph_id") val glyphId: Int,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "line_number") val lineNumber: Int,
    @ColumnInfo(name = "sura_number") val suraNumber: Int,
    @ColumnInfo(name = "ayah_number") val ayahNumber: Int,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "min_x") val minX: Int,
    @ColumnInfo(name = "max_x") val maxX: Int,
    @ColumnInfo(name = "min_y") val minY: Int,
    @ColumnInfo(name = "max_y") val maxY: Int,
)
