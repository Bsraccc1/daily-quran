package com.quranreader.custom.data.audio.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks a fully-downloaded surah recitation for offline playback.
 *
 * One row per (reciter, surah) pair. Inserted by [AudioDownloadWorker] on success;
 * deleted by ManageDownloadsScreen.
 *
 * @property id composite key "${reciterId}-${surahNumber}" — globally unique per (reciter, surah)
 * @property reciterId matches [com.quranreader.custom.data.audio.ReciterConfig.id]
 * @property surahNumber 1..114
 * @property ayahCount number of ayah audio files actually downloaded
 * @property totalBytes total size on disk for this download
 * @property downloadedAt epoch milliseconds when download completed
 */
@Entity(
    tableName = "downloaded_surahs",
    indices = [Index(value = ["reciterId"])]
)
data class DownloadedSurah(
    @PrimaryKey val id: String,
    val reciterId: String,
    val surahNumber: Int,
    val ayahCount: Int,
    val totalBytes: Long,
    val downloadedAt: Long
) {
    companion object {
        /** Build the canonical primary-key id for a (reciterId, surahNumber) tuple. */
        fun idFor(reciterId: String, surahNumber: Int): String =
            "$reciterId-$surahNumber"
    }
}
