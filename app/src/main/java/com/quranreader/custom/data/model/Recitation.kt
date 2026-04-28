package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per recitation known to quran.com — used to populate the
 * reciter picker dynamically. [recitationId] is the canonical
 * quran.com integer identifier and is what we hand to
 * [com.quranreader.custom.data.audio.timing.AyahTimingApi.getTimings]
 * to drive the highlight sync.
 *
 * `audioUrlBase` is filled in lazily once we've resolved the URL
 * pattern for a reciter (by sniffing the first ayah of surah 1 from
 * the timings endpoint). We strip the trailing `001001.mp3` so the
 * stored value looks like `https://verses.quran.com/Alafasy/mp3/`,
 * mirroring [com.quranreader.custom.data.audio.ReciterConfig.baseUrl]
 * — that lets the existing [com.quranreader.custom.data.audio.AudioUrlResolver]
 * work unchanged.
 */
@Entity(tableName = "recitations")
data class Recitation(
    @PrimaryKey val recitationId: Int,
    val reciterName: String,
    val style: String? = null,
    val translatedName: String? = null,
    val audioUrlBase: String? = null
) {
    /** English-friendly display name, e.g. "Mishary Alafasy · Murattal". */
    val displayName: String
        get() {
            val base = translatedName ?: reciterName
            return if (style.isNullOrBlank()) base else "$base · $style"
        }
}
