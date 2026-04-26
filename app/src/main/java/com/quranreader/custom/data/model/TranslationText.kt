package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translations",
    indices = [Index(value = ["surahNumber", "ayahNumber", "languageCode"], unique = true)]
)
data class TranslationText(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val surahNumber: Int,
    val ayahNumber: Int,
    val languageCode: String,
    val translationName: String,
    val text: String
)
