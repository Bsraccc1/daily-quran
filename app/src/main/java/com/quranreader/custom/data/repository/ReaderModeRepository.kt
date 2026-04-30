package com.quranreader.custom.data.repository

import com.quranreader.custom.data.preferences.ReadingMode
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.domain.SurahAyah
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the reader's current presentation mode
 * and last-read `(surah, ayah)`. A thin wrapper around
 * [UserPreferences] so the rest of the codebase can depend on this
 * narrow interface instead of the much larger `UserPreferences`.
 *
 * **Position semantics**: `lastPosition` is the cross-mode anchor.
 *  - Mushaf reader: writes both `LAST_PAGE_KEY` (its existing flow) and
 *    `LAST_SURAH_KEY/LAST_AYAH_KEY`.
 *  - Translation reader: writes only `LAST_SURAH_KEY/LAST_AYAH_KEY`.
 *
 * The "Continue Reading" CTA reads `lastPosition` regardless of which
 * reader saved it, so flipping modes never loses progress.
 */
@Singleton
class ReaderModeRepository @Inject constructor(
    private val userPreferences: UserPreferences,
) {

    /** Currently-selected reading mode. Defaults to [ReadingMode.MUSHAF]. */
    val readingMode: Flow<ReadingMode> = userPreferences.readingMode

    suspend fun setReadingMode(mode: ReadingMode) =
        userPreferences.setReadingMode(mode)

    /**
     * Cross-mode last-read anchor. Combines the two scalar prefs
     * into a single observable [SurahAyah]. Defaults to (1, 1).
     */
    val lastPosition: Flow<SurahAyah> = combine(
        userPreferences.lastSurah,
        userPreferences.lastAyah,
    ) { surah, ayah -> SurahAyah(surah, ayah) }

    suspend fun setLastPosition(surah: Int, ayah: Int) =
        userPreferences.setLastSurahAyah(surah, ayah)
}
