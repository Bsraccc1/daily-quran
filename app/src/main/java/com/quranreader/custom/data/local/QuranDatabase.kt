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
    version = 8,
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
    }
}
