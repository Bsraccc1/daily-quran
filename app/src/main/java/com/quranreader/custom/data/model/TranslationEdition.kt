package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per translation edition known to quran.com (Sahih
 * International, Indonesian Ministry, Pickthall, Hilali-Khan, …).
 * Cached locally so the picker is offline-readable and the
 * download/installed bookkeeping has a stable foreign key
 * ([editionId] = quran.com `translation_id`).
 *
 * `isDownloaded` / `verseCount` / `lastDownloadedAt` are derived
 * from the [com.quranreader.custom.data.local.TranslationDao] query
 * `getInstalledStatus(editionId)` and persisted here so the picker
 * can render those columns without joining on every render.
 */
@Entity(tableName = "translation_editions")
data class TranslationEdition(
    @PrimaryKey val editionId: Int,
    val name: String,
    val authorName: String? = null,
    val languageName: String? = null,
    val slug: String? = null,
    val isDownloaded: Boolean = false,
    val verseCount: Int = 0,
    val lastDownloadedAt: Long = 0L
)
