package com.quranreader.custom.data.model

import androidx.room.Entity

/**
 * Per-ayah Uthmani Arabic text. Seeded once on first launch from
 * `assets/quran_data/quran_uthmani.json` by [com.quranreader.custom.data.seed.ArabicVerseSeeder].
 * Read-only at runtime — Translation reader queries by `(surah, ayah)`
 * range. Source: Tanzil project; license shipped at
 * `assets/quran_data/LICENSE-tanzil.txt` and rendered in About.
 */
@Entity(
    tableName = "arabic_verses",
    primaryKeys = ["surahNumber", "ayahNumber"],
)
data class ArabicVerse(
    val surahNumber: Int,
    val ayahNumber: Int,
    val textUthmani: String,
)
