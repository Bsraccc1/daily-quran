package com.quranreader.custom.data.audio.timing

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * quran.com timing API.
 *
 * Endpoint: `GET https://api.quran.com/api/v4/recitations/{recitationId}/by_chapter/{chapter}`
 *
 * Returns per-ayah audio offsets for a single (recitation, chapter) pair.
 */
interface AyahTimingApi {

    @GET("api/v4/recitations/{recitationId}/by_chapter/{chapter}")
    suspend fun getTimings(
        @Path("recitationId") recitationId: String,
        @Path("chapter") chapter: Int
    ): AyahTimingResponse
}

/**
 * Top-level response shape from quran.com.
 *
 * Sample (truncated):
 * ```json
 * {
 *   "audio_files": [
 *     { "verse_key": "1:1", "timestamp_from": 0, "timestamp_to": 7000 },
 *     { "verse_key": "1:2", "timestamp_from": 7000, "timestamp_to": 12500 }
 *   ]
 * }
 * ```
 */
data class AyahTimingResponse(
    @SerializedName("audio_files") val audioFiles: List<AyahTimingDto> = emptyList()
)

/**
 * Individual ayah timing record from quran.com.
 *
 * @property verseKey "{surah}:{ayah}" (e.g. "1:1")
 * @property url full audio URL for this ayah (e.g.
 *   `https://verses.quran.com/Alafasy/mp3/001001.mp3`). Only present
 *   when the response is fetched with the verse-by-verse style;
 *   nullable for safety. Used by [AudioUrlResolver] when the user has
 *   picked a reciter from the dynamic quran.com catalogue (i.e. one
 *   without a hard-coded everyayah formatPattern).
 */
data class AyahTimingDto(
    @SerializedName("verse_key") val verseKey: String,
    @SerializedName("timestamp_from") val timestampFrom: Long?,
    @SerializedName("timestamp_to") val timestampTo: Long?,
    @SerializedName("url") val url: String? = null
) {
    /** Parse "{surah}:{ayah}" into Pair<Int, Int>, or null if malformed. */
    fun parseSurahAyah(): Pair<Int, Int>? {
        val parts = verseKey.split(':')
        if (parts.size != 2) return null
        val surah = parts[0].toIntOrNull() ?: return null
        val ayah = parts[1].toIntOrNull() ?: return null
        return surah to ayah
    }
}
