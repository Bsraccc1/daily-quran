package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One translated ayah row. v9 adds [editionId] so the table can hold
 * multiple translations per language (e.g. Sahih International + Pickthall
 * + Yusuf Ali in English) keyed by quran.com's `translation_id`. The old
 * `(surahNumber, ayahNumber, languageCode)` uniqueness gave way to
 * `(surahNumber, ayahNumber, editionId)` so a single language can hold
 * many editions concurrently.
 *
 * [languageCode] is kept for backward compatibility with widgets / the
 * daily-verse worker — both still query by language. New code should
 * prefer [editionId] queries which surface the user's chosen edition
 * directly.
 */
@Entity(
    tableName = "translations",
    indices = [
        Index(value = ["surahNumber", "ayahNumber", "editionId"], unique = true),
        Index(value = ["editionId"]),
        Index(value = ["languageCode"])
    ]
)
data class TranslationText(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val surahNumber: Int,
    val ayahNumber: Int,
    val editionId: Int = 0,
    val languageCode: String,
    val translationName: String,
    val text: String
)
