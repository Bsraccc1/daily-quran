package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single translated ayah row.
 *
 * Schema v9: a translation is identified by [translationId] (matches a
 * quran.com `resources/translations` entry, e.g. 131 = Sahih
 * International). Multiple translations per language are now supported,
 * so [languageCode] is kept for filtering/grouping but is no longer
 * unique on its own.
 */
@Entity(
    tableName = "translations",
    indices = [
        Index(value = ["surahNumber", "ayahNumber", "translationId"], unique = true),
        Index(value = ["translationId"]),
        Index(value = ["languageCode"]),
    ]
)
data class TranslationText(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val surahNumber: Int,
    val ayahNumber: Int,
    val translationId: Int,
    val languageCode: String,
    val translationName: String,
    val authorName: String = "",
    val slug: String = "",
    val text: String,
)
