package com.quranreader.custom.data.model

import androidx.room.Entity

/**
 * Per-ayah Latin-script transliteration, fetched on demand from
 * quran.com and cached forever in Room. Mirrors the cache pattern of
 * [TranslationText]. Keyed by `(surah, ayah, languageCode)` so a user
 * can switch transliteration variants without losing cached data.
 */
@Entity(
    tableName = "transliterations",
    primaryKeys = ["surahNumber", "ayahNumber", "languageCode"],
)
data class Transliteration(
    val surahNumber: Int,
    val ayahNumber: Int,
    val languageCode: String,
    val text: String,
)
