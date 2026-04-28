package com.quranreader.custom.data.audio.timing

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for ayah timing data with cache-first strategy:
 * 1. Read from Room. If present -> return cached.
 * 2. Otherwise, fetch from quran.com API + persist to Room + return.
 *
 * Failure semantics: on API error, returns whatever's cached (possibly empty).
 * Caller should treat empty list as "no timing available, fall back to MediaItem-level highlight".
 */
@Singleton
class AyahTimingRepository @Inject constructor(
    private val api: AyahTimingApi,
    private val dao: AyahTimingDao
) {

    /**
     * Get timings for (reciter, surah). Cache-first; falls back to API + persists.
     *
     * @param reciterId one of [com.quranreader.custom.data.audio.ReciterConfig.id]
     * @param surahNumber 1..114
     * @return list of [AyahTiming]; empty if API failed and no cache exists
     */
    suspend fun getTimings(reciterId: String, surahNumber: Int): List<AyahTiming> {
        if (dao.hasTimingsFor(reciterId, surahNumber)) {
            return dao.getBySurah(reciterId, surahNumber)
        }

        val recitationId = ReciterRecitationMap.recitationIdFor(reciterId)
            ?: return emptyList() // unmapped reciter -> caller falls back

        val timings = try {
            val response = api.getTimings(recitationId, surahNumber)
            response.audioFiles.mapNotNull { dto ->
                val (surah, ayah) = dto.parseSurahAyah() ?: return@mapNotNull null
                if (surah != surahNumber) return@mapNotNull null
                val start = dto.timestampFrom ?: return@mapNotNull null
                val end = dto.timestampTo ?: start
                AyahTiming(
                    reciterId = reciterId,
                    surah = surah,
                    ayah = ayah,
                    startMs = start,
                    endMs = end
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch timings for reciterId=$reciterId surah=$surahNumber", e)
            emptyList()
        }

        if (timings.isNotEmpty()) {
            dao.insertAll(timings)
        }

        return timings.ifEmpty { dao.getBySurah(reciterId, surahNumber) }
    }

    companion object {
        private const val TAG = "AyahTimingRepo"
    }
}

/**
 * Maps internal `ReciterConfig.id` (the slug used in DataStore /
 * AudioService) to quran.com `recitation_id` (the integer used by
 * [AyahTimingApi]). The mapping is consulted *only* for highlight
 * sync — playback URLs come from each reciter's everyayah base, not
 * from quran.com — so an unmapped entry just disables sync for that
 * reciter while audio still plays correctly.
 *
 * Verified IDs (quran.com `/api/v4/resources/recitations`):
 *   1  AbdulBaset AbdulSamad (Murattal)
 *   2  AbdulBaset AbdulSamad (Mujawwad)
 *   3  Abdurrahmaan As-Sudais
 *   6  Mahmoud Khalil Al-Husary (Mu'allim)
 *   7  Mishari Rashid al-Afasy
 *   8  Mahmoud Khalil Al-Husary (Mujawwad)
 *   9  Mohamed Siddiq al-Minshawi (Mujawwad)
 *  10  Mohamed Siddiq al-Minshawi (Murattal)
 *  11  Sa'ud Ash-Shuraym
 */
object ReciterRecitationMap {
    private val map = mapOf(
        "abdul_basit_murattal" to "1",
        "abdul_basit_mujawwad" to "2",
        "abdurrahman_as_sudais" to "3",
        "mahmoud_husary" to "6",
        "mishary_alafasy" to "7",
        "minshawi_murattal" to "10",
        "saud_al_shuraim" to "11",
        // Reciters without a confirmed quran.com timing — playback
        // works (everyayah audio) but per-ayah highlight falls back
        // to MediaItem-level sync. Keep their entries here as an
        // explicit `null` so the table stays exhaustive.
        "maher_al_muaiqly" to null,
        "saad_al_ghamdi" to null,
        "ahmed_al_ajamy" to null,
        "ali_al_hudhaify" to null,
        "salah_bukhatir" to null,
    )

    fun recitationIdFor(reciterId: String): String? = map[reciterId]
}
