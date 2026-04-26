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
 * Maps internal reciterId to quran.com recitation_id.
 *
 * quran.com recitation IDs are integers; common ones (verified from quran.com `recitations` API):
 *  - 1 = AbdulBaset AbdulSamad (Murattal)
 *  - 7 = Mishary Rashid Alafasy
 *  - 11 = Mahmoud Khalil Al-Husary
 *  - 6 = Abdurrahman As-Sudais (some recitations use ID 6 or 12)
 *
 * For unmapped reciters, repository returns empty list -> sync engine falls back to
 * MediaItem-level highlight.
 */
object ReciterRecitationMap {
    private val map = mapOf(
        "abdul_basit_murattal" to "1",
        "mishary_alafasy" to "7",
        "abdurrahman_as_sudais" to "11"
    )

    fun recitationIdFor(reciterId: String): String? = map[reciterId]
}
