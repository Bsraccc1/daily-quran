package com.quranreader.custom.domain

/**
 * Cross-mode position primitive — `(surah, ayah)` is the
 * shared anchor between the Mushaf reader (page-based) and the
 * Translation reader (verse-based). 1-indexed for both axes (Quranic
 * tradition); `(1, 1)` is Al-Fatihah verse 1.
 */
data class SurahAyah(val surah: Int, val ayah: Int) {
    override fun toString(): String = "$surah:$ayah"

    companion object {
        /** Al-Fatihah verse 1 — the universal "start of the Quran" position. */
        val START = SurahAyah(1, 1)
    }
}
