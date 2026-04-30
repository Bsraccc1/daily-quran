package com.quranreader.custom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quranreader.custom.data.audio.download.DownloadedSurah
import com.quranreader.custom.data.audio.download.DownloadedSurahDao
import com.quranreader.custom.data.audio.timing.AyahTiming
import com.quranreader.custom.data.audio.timing.AyahTimingDao
import com.quranreader.custom.data.memorization.MemorizationSession
import com.quranreader.custom.data.memorization.MemorizationSessionDao
import com.quranreader.custom.data.model.ArabicVerse
import com.quranreader.custom.data.model.Bookmark
import com.quranreader.custom.data.model.AyahCoordinate
import com.quranreader.custom.data.model.Recitation
import com.quranreader.custom.data.model.Transliteration
import com.quranreader.custom.data.model.TranslationEdition
import com.quranreader.custom.data.model.TranslationText

@Database(
    entities = [
        Bookmark::class,
        AyahCoordinate::class,
        TranslationText::class,
        TranslationEdition::class,
        Recitation::class,
        DownloadedSurah::class,
        AyahTiming::class,
        MemorizationSession::class,
        ArabicVerse::class,
        Transliteration::class,
    ],
    version = 10,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun ayahCoordinateDao(): AyahCoordinateDao
    abstract fun translationDao(): TranslationDao
    abstract fun translationEditionDao(): TranslationEditionDao
    abstract fun recitationDao(): RecitationDao
    abstract fun downloadedSurahDao(): DownloadedSurahDao
    abstract fun ayahTimingDao(): AyahTimingDao
    abstract fun memorizationSessionDao(): MemorizationSessionDao
    abstract fun arabicVerseDao(): ArabicVerseDao
    abstract fun transliterationDao(): TransliterationDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS translations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surahNumber INTEGER NOT NULL,
                        ayahNumber INTEGER NOT NULL,
                        languageCode TEXT NOT NULL,
                        translationName TEXT NOT NULL,
                        text TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_translations_surahNumber_ayahNumber_languageCode ON translations (surahNumber, ayahNumber, languageCode)")
            }
        }

        /** v5: add `downloaded_surahs` table for offline audio download tracking (Story 1 / REQ-001). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS downloaded_surahs (
                        id TEXT PRIMARY KEY NOT NULL,
                        reciterId TEXT NOT NULL,
                        surahNumber INTEGER NOT NULL,
                        ayahCount INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        downloadedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_surahs_reciterId ON downloaded_surahs (reciterId)")
            }
        }

        /** v6: add `ayah_timings` table for highlight sync (Story 2 / REQ-004). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ayah_timings (
                        reciterId TEXT NOT NULL,
                        surah INTEGER NOT NULL,
                        ayah INTEGER NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        PRIMARY KEY (reciterId, surah, ayah)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ayah_timings_reciterId_surah ON ayah_timings (reciterId, surah)")
            }
        }

        /** v7: add `memorization_sessions` table for hifz mode (Story 3 / REQ-009). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memorization_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surahStart INTEGER NOT NULL,
                        ayahStart INTEGER NOT NULL,
                        surahEnd INTEGER NOT NULL,
                        ayahEnd INTEGER NOT NULL,
                        repeatTarget INTEGER NOT NULL,
                        repeatsCompleted INTEGER NOT NULL,
                        totalSeconds INTEGER NOT NULL,
                        autoAdvance INTEGER NOT NULL DEFAULT 0,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memorization_sessions_startedAt ON memorization_sessions (startedAt)")
            }
        }

        /**
         * v8: drop the legacy `quran_texts` table. Mushaf rendering moved to
         * bundled WebP page images; we no longer need an Arabic text DB. The
         * table held no user-generated data, so dropping it is safe — older
         * users will simply lose a never-used cache row.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS quran_texts")
            }
        }

        /**
         * v9 — Translations are now keyed by quran.com `translation_id`
         * (`editionId`) instead of language code, so the table can hold
         * multiple translations per language at once (Sahih + Pickthall +
         * Yusuf Ali, all in `en`). Adds the column, backfills it for the
         * two legacy editions ('en' → 131 Saheeh International,
         * 'id' → 33 Indonesian Ministry), swaps the unique index, and
         * creates the two new catalogue tables that back the picker.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add editionId column (default 0 for unmapped legacy rows).
                db.execSQL("ALTER TABLE translations ADD COLUMN editionId INTEGER NOT NULL DEFAULT 0")

                // 2. Backfill legacy rows — 'en' was Saheeh International
                //    (quran.com id 131) and 'id' was Indonesian Ministry
                //    (quran.com id 33). Anything else stays at editionId=0
                //    and the picker treats it as legacy / re-downloadable.
                db.execSQL("UPDATE translations SET editionId = 131 WHERE languageCode = 'en'")
                db.execSQL("UPDATE translations SET editionId = 33 WHERE languageCode = 'id'")

                // 3. Swap the uniqueness from (surah, ayah, lang) to
                //    (surah, ayah, editionId). Keep secondary indices for
                //    legacy lang-based queries (widgets, daily-verse worker).
                db.execSQL("DROP INDEX IF EXISTS index_translations_surahNumber_ayahNumber_languageCode")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_translations_surahNumber_ayahNumber_editionId ON translations (surahNumber, ayahNumber, editionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translations_editionId ON translations (editionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translations_languageCode ON translations (languageCode)")

                // 4. Catalogue tables for the picker (cached editions /
                //    recitations from quran.com so the UI stays offline-
                //    readable after the first network refresh).
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS translation_editions (
                        editionId INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        authorName TEXT,
                        languageName TEXT,
                        slug TEXT,
                        isDownloaded INTEGER NOT NULL DEFAULT 0,
                        verseCount INTEGER NOT NULL DEFAULT 0,
                        lastDownloadedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recitations (
                        recitationId INTEGER PRIMARY KEY NOT NULL,
                        reciterName TEXT NOT NULL,
                        style TEXT,
                        translatedName TEXT,
                        audioUrlBase TEXT
                    )
                """)

                // 5. Seed the legacy editions so the picker shows
                //    them as already-downloaded after the migration.
                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT OR REPLACE INTO translation_editions
                        (editionId, name, authorName, languageName, slug, isDownloaded, verseCount, lastDownloadedAt)
                    SELECT 131, 'Saheeh International', 'Saheeh International', 'english', 'saheeh-international',
                           CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END,
                           COUNT(*), $now
                    FROM translations WHERE editionId = 131
                """)
                db.execSQL("""
                    INSERT OR REPLACE INTO translation_editions
                        (editionId, name, authorName, languageName, slug, isDownloaded, verseCount, lastDownloadedAt)
                    SELECT 33, 'Indonesian Ministry of Religious Affairs', 'Indonesian Ministry', 'indonesian', 'id-indonesian',
                           CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END,
                           COUNT(*), $now
                    FROM translations WHERE editionId = 33
                """)
            }
        }

        /**
         * v10 — introduce per-verse Arabic text (`arabic_verses`) and
         * per-language transliteration (`transliterations`). Both
         * tables are content tables — keys are (surah, ayah) plus
         * languageCode for transliteration. Purely additive: zero
         * impact on user data already in v9 (bookmarks, sessions,
         * translations, etc.). The `arabic_verses` table is populated
         * post-migration by [com.quranreader.custom.data.seed.ArabicVerseSeeder]
         * from the bundled `assets/quran_data/quran_uthmani.json`.
         * Transliterations are populated lazily by
         * `TransliterationRepository` on the first juz-read in
         * Translation mode.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS arabic_verses (
                        surahNumber INTEGER NOT NULL,
                        ayahNumber  INTEGER NOT NULL,
                        textUthmani TEXT NOT NULL,
                        PRIMARY KEY (surahNumber, ayahNumber)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transliterations (
                        surahNumber INTEGER NOT NULL,
                        ayahNumber  INTEGER NOT NULL,
                        languageCode TEXT NOT NULL,
                        text TEXT NOT NULL,
                        PRIMARY KEY (surahNumber, ayahNumber, languageCode)
                    )
                """)
            }
        }
    }
}
