package com.quranreader.custom.data.audio.timing

import androidx.room.Entity
import androidx.room.Index

/**
 * Ayah timing data — start/end ms within a surah audio for a single ayah.
 *
 * Sourced from quran.com `recitations/{id}/by_chapter/{chapter}` API and persisted in Room
 * for offline-friendly highlight sync (REQ-004).
 *
 * Composite primary key on (reciterId, surah, ayah) ensures upsert per ayah.
 *
 * @property reciterId matches [com.quranreader.custom.data.audio.ReciterConfig.id]
 * @property surah 1..114
 * @property ayah 1..N within surah
 * @property startMs millisecond offset (within the per-ayah audio file) where this ayah begins
 * @property endMs millisecond offset where the ayah ends; for per-ayah audio (everyayah.com)
 *           this typically equals the file duration
 */
@Entity(
    tableName = "ayah_timings",
    primaryKeys = ["reciterId", "surah", "ayah"],
    indices = [Index(value = ["reciterId", "surah"])]
)
data class AyahTiming(
    val reciterId: String,
    val surah: Int,
    val ayah: Int,
    val startMs: Long,
    val endMs: Long
)
