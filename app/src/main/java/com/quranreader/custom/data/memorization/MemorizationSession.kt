package com.quranreader.custom.data.memorization

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single memorization (hifz) session — repeat-N-times loop on an ayah range.
 *
 * Created when user starts hifz mode; updated on pause/complete; persisted indefinitely
 * so the user can review history (Story 3 / REQ-009).
 *
 * @property surahStart starting surah (1..114)
 * @property ayahStart starting ayah within [surahStart]
 * @property surahEnd ending surah (typically same as surahStart unless user crossed boundary)
 * @property ayahEnd ending ayah
 * @property repeatTarget number of repeats requested per ayah (3, 5, 10, or custom)
 * @property repeatsCompleted total repeats actually played across all ayahs in the range
 * @property totalSeconds elapsed seconds counted while audio was actively playing
 * @property autoAdvance whether session auto-advances ayahs at counter==0
 * @property startedAt session start (epoch ms)
 * @property completedAt session end (epoch ms); null if still active / saved-mid
 */
@Entity(
    tableName = "memorization_sessions",
    indices = [Index(value = ["startedAt"])]
)
data class MemorizationSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val surahStart: Int,
    val ayahStart: Int,
    val surahEnd: Int,
    val ayahEnd: Int,
    val repeatTarget: Int,
    val repeatsCompleted: Int,
    val totalSeconds: Int,
    val autoAdvance: Boolean,
    val startedAt: Long,
    val completedAt: Long?
)
