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
import com.quranreader.custom.data.model.Bookmark
import com.quranreader.custom.data.model.AyahCoordinate
import com.quranreader.custom.data.model.TranslationText

@Database(
    entities = [
        Bookmark::class,
        AyahCoordinate::class,
        TranslationText::class,
        DownloadedSurah::class,
        AyahTiming::class,
        MemorizationSession::class
    ],
    version = 9,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun ayahCoordinateDao(): AyahCoordinateDao
    abstract fun translationDao(): TranslationDao
    abstract fun downloadedSurahDao(): DownloadedSurahDao
    abstract fun ayahTimingDao(): AyahTimingDao
    abstract fun memorizationSessionDao(): MemorizationSessionDao

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
         * v9: extend `translations` to support multiple editions per
         * language. The previous schema keyed uniqueness on
         * `(surahNumber, ayahNumber, languageCode)`, which forced one
         * translation per language. v9 keys uniqueness on
         * `translationId` (matches a quran.com resource ID) and adds
         * `authorName` / `slug` columns. Existing rows are migrated
         * to translationId 131 (Sahih, English) or 33 (Indonesian
         * Ministry) based on their old languageCode.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Build a fresh table with the v9 shape, copy existing
                // data into it, then swap names. This avoids ALTER
                // TABLE limitations around index uniqueness changes.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS translations_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surahNumber INTEGER NOT NULL,
                        ayahNumber INTEGER NOT NULL,
                        translationId INTEGER NOT NULL,
                        languageCode TEXT NOT NULL,
                        translationName TEXT NOT NULL,
                        authorName TEXT NOT NULL DEFAULT '',
                        slug TEXT NOT NULL DEFAULT '',
                        text TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO translations_new
                        (id, surahNumber, ayahNumber, translationId, languageCode,
                         translationName, authorName, slug, text)
                    SELECT id, surahNumber, ayahNumber,
                           CASE languageCode
                               WHEN 'en' THEN 131
                               WHEN 'id' THEN 33
                               ELSE 131
                           END AS translationId,
                           languageCode,
                           translationName,
                           '' AS authorName,
                           '' AS slug,
                           text
                    FROM translations
                """)
                db.execSQL("DROP TABLE translations")
                db.execSQL("ALTER TABLE translations_new RENAME TO translations")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_translations_surahNumber_ayahNumber_translationId ON translations (surahNumber, ayahNumber, translationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translations_translationId ON translations (translationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translations_languageCode ON translations (languageCode)")
            }
        }
    }
}
