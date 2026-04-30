package com.quranreader.custom.data.preferences

/**
 * Reader presentation style. Persisted as the enum name in DataStore
 * (see [UserPreferences.readingMode]). Default is [MUSHAF] for backward
 * compatibility with every pre-v10 user.
 */
enum class ReadingMode {
    /** WebP page-by-page renderer (existing MushafReaderScreen). */
    MUSHAF,

    /** Per-juz scrollable verse list with Arabic + transliteration + translation. */
    TRANSLATION;

    companion object {
        /**
         * Round-trip from [DataStore]. Null or unknown values fall
         * back to [MUSHAF] so a corrupted preference can never lock
         * the user out of the reader.
         */
        fun fromName(name: String?): ReadingMode =
            values().firstOrNull { it.name == name } ?: MUSHAF
    }
}
