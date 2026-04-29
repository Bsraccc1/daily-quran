package com.quranreader.custom.data.model

/**
 * A translation edition advertised by the quran.com API
 * (`/api/v4/resources/translations`). Lightweight value type used by
 * the manager UI; not persisted to Room.
 */
data class AvailableTranslation(
    val id: Int,
    val name: String,
    val authorName: String,
    val slug: String,
    val languageName: String,
    val languageCode: String,
)
