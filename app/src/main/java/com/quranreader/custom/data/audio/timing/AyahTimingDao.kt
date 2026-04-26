package com.quranreader.custom.data.audio.timing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `ayah_timings` table — stores per-ayah audio offsets used by
 * [com.quranreader.custom.data.audio.sync.HighlightSyncEngine] for visual sync with playback.
 */
@Dao
interface AyahTimingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AyahTiming>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AyahTiming)

    /** Stream all timings for a (reciter, surah) pair. Ordered by ayah ascending. */
    @Query("SELECT * FROM ayah_timings WHERE reciterId = :reciterId AND surah = :surah ORDER BY ayah ASC")
    fun observeBySurah(reciterId: String, surah: Int): Flow<List<AyahTiming>>

    /** One-shot fetch (used by sync engine on play start). */
    @Query("SELECT * FROM ayah_timings WHERE reciterId = :reciterId AND surah = :surah ORDER BY ayah ASC")
    suspend fun getBySurah(reciterId: String, surah: Int): List<AyahTiming>

    /** Lookup a specific ayah's timing. */
    @Query("SELECT * FROM ayah_timings WHERE reciterId = :reciterId AND surah = :surah AND ayah = :ayah LIMIT 1")
    suspend fun getOne(reciterId: String, surah: Int, ayah: Int): AyahTiming?

    /** True if at least one timing row exists for (reciter, surah). */
    @Query("SELECT EXISTS(SELECT 1 FROM ayah_timings WHERE reciterId = :reciterId AND surah = :surah)")
    suspend fun hasTimingsFor(reciterId: String, surah: Int): Boolean

    @Query("DELETE FROM ayah_timings WHERE reciterId = :reciterId")
    suspend fun deleteByReciter(reciterId: String)
}
