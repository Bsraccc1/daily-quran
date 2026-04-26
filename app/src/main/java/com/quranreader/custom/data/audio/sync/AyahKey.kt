package com.quranreader.custom.data.audio.sync

/**
 * Stable identifier for a single ayah used by [HighlightSyncEngine] and the highlight overlay.
 *
 * @property surah 1..114
 * @property ayah 1..N within surah
 */
data class AyahKey(val surah: Int, val ayah: Int) {
    companion object {
        /** Construct from quran.com `verse_key` format "1:1". Returns null if malformed. */
        fun fromVerseKey(verseKey: String): AyahKey? {
            val parts = verseKey.split(':')
            if (parts.size != 2) return null
            val s = parts[0].toIntOrNull() ?: return null
            val a = parts[1].toIntOrNull() ?: return null
            return AyahKey(s, a)
        }
    }
}
