package com.quranreader.custom.data

/**
 * Constants for Daily Quran app
 * Adapted from quran_android data layer
 */
object Constants {

    // Quran Structure
    const val PAGES_FIRST = 1
    const val PAGES_LAST = 604
    const val PAGES_COUNT = 604
    
    const val SURA_FIRST = 1
    const val SURA_LAST = 114
    const val SURAS_COUNT = 114
    
    const val JUZ_COUNT = 30
    const val JUZ2_COUNT = 60 // Half Juz
    const val HIZB_COUNT = 60
    
    const val NO_PAGE = -1
    const val MAX_RECENT_PAGES = 3

    // Display Settings
    const val DEFAULT_TEXT_SIZE = 15
    const val DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS = 255
    const val DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS = 0

    // Notification IDs
    const val NOTIFICATION_ID_AUDIO_PLAYBACK = 4
    const val NOTIFICATION_ID_DOWNLOADING = 1
    const val NOTIFICATION_ID_DOWNLOADING_COMPLETE = 2
    const val NOTIFICATION_ID_DOWNLOADING_ERROR = 3

    // Notification Channels
    const val AUDIO_CHANNEL = "quran_audio_playback"
    const val DOWNLOAD_CHANNEL = "quran_download_progress"

    // DataStore Keys
    const val PREF_LAST_PAGE = "lastPage"
    const val PREF_DISPLAY_MODE = "displayMode" // LIGHT, DARK, SYSTEM
    const val PREF_APP_LANGUAGE = "appLanguage" // en, id
    const val PREF_AUDIO_REPEAT_COUNT = "audioRepeatCount" // 0 = infinite, 1-5 = count
    const val PREF_AUDIO_FROM_AYAH = "audioFromAyah"
    const val PREF_AUDIO_TO_AYAH = "audioToAyah"
    
    // Session Management
    const val PREF_SESSION_ACTIVE = "sessionActive"
    const val PREF_SESSION_START_PAGE = "sessionStartPage"
    const val PREF_SESSION_TARGET_PAGES = "sessionTargetPages"
    const val PREF_SESSION_CURRENT_PAGE = "sessionCurrentPage"

    // Display Modes
    const val DISPLAY_MODE_LIGHT = "LIGHT"
    const val DISPLAY_MODE_DARK = "DARK"
    const val DISPLAY_MODE_SYSTEM = "SYSTEM"

    // Languages
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_INDONESIAN = "id"

    // Audio Repeat Options
    const val REPEAT_ONCE = 1
    const val REPEAT_TWICE = 2
    const val REPEAT_THREE = 3
    const val REPEAT_FIVE = 5
    const val REPEAT_INFINITE = 0

    // Asset Paths
    const val FONT_UTHMANIC = "uthmanic_hafs_ver12.otf"
    const val FONT_UTHMAN_NASKH = "UthmanTN1Ver10.otf"
    const val FONT_KITAB = "kitab.ttf"
    const val FONT_DYSLEXIC = "OpenDyslexic.otf"
    const val DATABASE_WORD_ALIGNMENT = "word_alignment.db"

    // Mushaf page image path pattern (bundled transparent WebP).
    // page_%03d.webp lives in assets/mushaf_pages/, drawn by
    // ui/components/mushaf/MushafImageRenderer.
    const val MUSHAF_PAGE_IMAGE_PATH = "file:///android_asset/mushaf_pages/page_%03d.webp"

    // Audio File Path Pattern (Surah/Ayah format: 001001.mp3)
    const val AUDIO_FILE_PATH = "file:///android_asset/audio/%s/%03d%03d.mp3"

    // App Info
    const val APP_NAME = "Daily Quran"
    const val APP_VERSION = "1.0.0"
    const val PACKAGE_NAME = "com.quranreader.custom"
}
