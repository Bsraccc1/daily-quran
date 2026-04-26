package com.quranreader.custom.data.audio.download

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `downloaded_surahs` table tracking offline-downloaded recitations.
 */
@Dao
interface DownloadedSurahDao {

    /** Insert (or replace) a downloaded surah row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadedSurah)

    /** Delete a specific row. */
    @Delete
    suspend fun delete(item: DownloadedSurah)

    /** Delete all rows (e.g. user "Delete all downloads"). */
    @Query("DELETE FROM downloaded_surahs")
    suspend fun deleteAll()

    /** Stream all rows ordered by reciter then surah. */
    @Query("SELECT * FROM downloaded_surahs ORDER BY reciterId ASC, surahNumber ASC")
    fun observeAll(): Flow<List<DownloadedSurah>>

    /** Stream rows for a single reciter. */
    @Query("SELECT * FROM downloaded_surahs WHERE reciterId = :reciterId ORDER BY surahNumber ASC")
    fun observeByReciter(reciterId: String): Flow<List<DownloadedSurah>>

    /** Lookup a single (reciter, surah) row, returning null if not downloaded. */
    @Query("SELECT * FROM downloaded_surahs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadedSurah?

    /** Sum total bytes across all downloads — used by Manage Downloads UI. */
    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloaded_surahs")
    fun observeTotalBytes(): Flow<Long>

    /** True if a (reciter, surah) is fully downloaded. */
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_surahs WHERE id = :id)")
    suspend fun isDownloaded(id: String): Boolean
}
