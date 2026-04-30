# Translation Reading Mode — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Translation reading mode (per-Juz scrollable list of verse cards: Arabic + transliteration + translation + action row) alongside the existing Mushaf page-by-page reader, with a Settings toggle and a top-bar quick switch in both readers. Position and bookmarks sync across modes.

**Architecture:** Bundle Uthmani Arabic text in `assets/quran_data/`, fetch translations + transliterations from quran.com (existing pattern), pure-JVM `PositionMapper` translates `(surah, ayah) ↔ mushaf page` via the bundled `ayahinfo.db`. Single-module `:app`, Hilt DI, Compose UI, Room v9 → v10 (purely additive migration).

**Tech Stack:** Kotlin 1.9.23, Jetpack Compose, Material 3, Hilt 2.48, Room v8/v10, Retrofit, Media3 ExoPlayer 1.3.1, KSP.

**Spec:** `docs/superpowers/specs/2026-04-30-translation-reading-mode-design.md`

---

## File Structure

**New files (`app/src/main/java/com/quranreader/custom/`):**

| Path | Responsibility |
|---|---|
| `data/preferences/ReadingMode.kt` | enum + serializer |
| `data/model/ArabicVerse.kt` | Room entity |
| `data/local/ArabicVerseDao.kt` | DB queries |
| `data/model/Transliteration.kt` | Room entity |
| `data/local/TransliterationDao.kt` | DB queries |
| `data/repository/ArabicVerseRepository.kt` | Read API for bundled verses |
| `data/repository/TransliterationRepository.kt` | quran.com fetch + cache |
| `data/repository/ReaderModeRepository.kt` | Mode + last-position SSOT |
| `data/seed/ArabicVerseSeeder.kt` | Asset → Room importer |
| `data/QuranInfoJuz.kt` | `juzOf(surah, ayah)` table + extension |
| `domain/SurahAyah.kt` | value type |
| `domain/PositionMapper.kt` | page ↔ surahAyah, pure JVM |
| `ui/viewmodel/TranslationReaderViewModel.kt` | state holder |
| `ui/screens/reading/TranslationReaderScreen.kt` | new screen |
| `ui/screens/reading/components/VerseCard.kt` | per-verse Composable |

**New asset:** `app/src/main/assets/quran_data/quran_uthmani.json` (~600 KB raw)

**New resources:** strings appended to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-in/strings.xml`

**Modified files:**

| Path | Change |
|---|---|
| `data/preferences/UserPreferences.kt` | + ReadingMode flow/setter, + lastSurahAyah keys |
| `data/local/QuranDatabase.kt` | v9 → v10 + MIGRATION_9_10, register 2 new DAOs |
| `data/remote/QuranComApi.kt` | + transliteration endpoint |
| `di/AppModule.kt` | register repos |
| `ui/navigation/NavGraph.kt` | + `Screen.TranslationReader` route + mode-aware router |
| `ui/screens/settings/SettingsScreen.kt` | + ReadingMode chip section |
| `ui/screens/settings/SettingsViewModel.kt` | + readingMode flow + setter |
| `ui/screens/reading/MushafReaderScreen.kt` | + SwapHoriz top-bar icon |
| `ui/screens/home/DashboardScreen.kt` | mode-aware "Continue Reading" CTA |
| `ui/screens/juz/JuzScreen.kt` | mode-aware navigation |
| `ui/screens/bookmarks/BookmarksScreen.kt` | mode-aware navigation |

**Tests (`app/src/test/java/com/quranreader/custom/`):**

| Path | Covers |
|---|---|
| `data/preferences/ReadingModeTest.kt` | enum round-trip |
| `data/QuranInfoJuzTest.kt` | juz boundaries |
| `domain/PositionMapperTest.kt` | page ↔ surahAyah round-trip |
| `data/repository/ReaderModeRepositoryTest.kt` | preferences flow |
| `ui/viewmodel/TranslationReaderViewModelTest.kt` | state transitions |
| `data/seed/ArabicVerseSeederTest.kt` | fixture import |

---

## Task 1: ReadingMode enum + UserPreferences keys

**Files:**
- Create: `app/src/main/java/com/quranreader/custom/data/preferences/ReadingMode.kt`
- Create: `app/src/test/java/com/quranreader/custom/data/preferences/ReadingModeTest.kt`
- Modify: `app/src/main/java/com/quranreader/custom/data/preferences/UserPreferences.kt`

- [ ] **Step 1: Write failing test** at `app/src/test/java/com/quranreader/custom/data/preferences/ReadingModeTest.kt`

```kotlin
package com.quranreader.custom.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingModeTest {
    @Test fun `default is MUSHAF`() {
        assertEquals(ReadingMode.MUSHAF, ReadingMode.fromName(null))
    }
    @Test fun `unknown name falls back to MUSHAF`() {
        assertEquals(ReadingMode.MUSHAF, ReadingMode.fromName("garbage"))
    }
    @Test fun `TRANSLATION round-trips`() {
        assertEquals(ReadingMode.TRANSLATION, ReadingMode.fromName("TRANSLATION"))
    }
}
```

- [ ] **Step 2: Run test, see it fail**

`./gradlew :app:testDebugUnitTest --tests "com.quranreader.custom.data.preferences.ReadingModeTest"`
Expected: FAIL — `ReadingMode` does not resolve.

- [ ] **Step 3: Create the enum**

`app/src/main/java/com/quranreader/custom/data/preferences/ReadingMode.kt`:

```kotlin
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
        fun fromName(name: String?): ReadingMode =
            values().firstOrNull { it.name == name } ?: MUSHAF
    }
}
```

- [ ] **Step 4: Run test, verify pass**

`./gradlew :app:testDebugUnitTest --tests "com.quranreader.custom.data.preferences.ReadingModeTest"` → PASS.

- [ ] **Step 5: Add prefs keys + flow + setter to `UserPreferences.kt`**

Add to the `companion object` (alongside existing keys):

```kotlin
        // ── Reading Mode (v10) ─────────────────────────────────────────────
        private val READING_MODE_KEY     = stringPreferencesKey("reading_mode")
        // Last-read (surah, ayah) — single source of truth across both
        // readers. The legacy LAST_PAGE_KEY is still written by the
        // mushaf reader so the Continue Reading CTA keeps working.
        private val LAST_SURAH_KEY       = intPreferencesKey("last_surah")
        private val LAST_AYAH_KEY        = intPreferencesKey("last_ayah")
```

Add public flows + setters (after the existing theme block, before session limits):

```kotlin
    // ── Reading Mode ──────────────────────────────────────────────────────────
    val readingMode: Flow<ReadingMode> = dataStore.data.map { prefs ->
        ReadingMode.fromName(prefs[READING_MODE_KEY])
    }
    suspend fun setReadingMode(mode: ReadingMode) = dataStore.edit {
        it[READING_MODE_KEY] = mode.name
    }

    val lastSurah: Flow<Int> = dataStore.data.map { it[LAST_SURAH_KEY] ?: 1 }
    val lastAyah:  Flow<Int> = dataStore.data.map { it[LAST_AYAH_KEY] ?: 1 }
    suspend fun setLastSurahAyah(surah: Int, ayah: Int) = dataStore.edit {
        it[LAST_SURAH_KEY] = surah
        it[LAST_AYAH_KEY]  = ayah
    }
```

- [ ] **Step 6: Build + commit**

`./gradlew :app:assembleDebug` → exit 0.
`git add app/src/main/java/com/quranreader/custom/data/preferences/ReadingMode.kt app/src/test/java/com/quranreader/custom/data/preferences/ReadingModeTest.kt app/src/main/java/com/quranreader/custom/data/preferences/UserPreferences.kt`
`git commit -m "feat(prefs): add ReadingMode enum and last-surah/ayah keys"`

---

## Task 2: ArabicVerse + Transliteration entities & DAOs

**Files:**
- Create: `app/src/main/java/com/quranreader/custom/data/model/ArabicVerse.kt`
- Create: `app/src/main/java/com/quranreader/custom/data/model/Transliteration.kt`
- Create: `app/src/main/java/com/quranreader/custom/data/local/ArabicVerseDao.kt`
- Create: `app/src/main/java/com/quranreader/custom/data/local/TransliterationDao.kt`

- [ ] **Step 1: Create `ArabicVerse.kt`**

```kotlin
package com.quranreader.custom.data.model

import androidx.room.Entity

@Entity(
    tableName = "arabic_verses",
    primaryKeys = ["surahNumber", "ayahNumber"],
)
data class ArabicVerse(
    val surahNumber: Int,
    val ayahNumber: Int,
    val textUthmani: String,
)
```

- [ ] **Step 2: Create `Transliteration.kt`**

```kotlin
package com.quranreader.custom.data.model

import androidx.room.Entity

@Entity(
    tableName = "transliterations",
    primaryKeys = ["surahNumber", "ayahNumber", "languageCode"],
)
data class Transliteration(
    val surahNumber: Int,
    val ayahNumber: Int,
    val languageCode: String,
    val text: String,
)
```

- [ ] **Step 3: Create `ArabicVerseDao.kt`**

```kotlin
package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.ArabicVerse

@Dao
interface ArabicVerseDao {
    @Query("SELECT * FROM arabic_verses WHERE surahNumber = :surah AND ayahNumber BETWEEN :from AND :to ORDER BY ayahNumber")
    suspend fun getRange(surah: Int, from: Int, to: Int): List<ArabicVerse>

    @Query("SELECT * FROM arabic_verses WHERE surahNumber = :surah AND ayahNumber = :ayah LIMIT 1")
    suspend fun get(surah: Int, ayah: Int): ArabicVerse?

    @Query("SELECT COUNT(*) FROM arabic_verses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(verses: List<ArabicVerse>)
}
```

- [ ] **Step 4: Create `TransliterationDao.kt`**

```kotlin
package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.Transliteration

@Dao
interface TransliterationDao {
    @Query("SELECT * FROM transliterations WHERE surahNumber = :surah AND ayahNumber BETWEEN :from AND :to AND languageCode = :lang ORDER BY ayahNumber")
    suspend fun getRange(surah: Int, from: Int, to: Int, lang: String): List<Transliteration>

    @Query("SELECT COUNT(*) FROM transliterations WHERE languageCode = :lang")
    suspend fun count(lang: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<Transliteration>)
}
```

- [ ] **Step 5: Build to verify compilation**

`./gradlew :app:compileDebugKotlin` → exit 0. (DB still v9, won't run until Task 3.)

- [ ] **Step 6: Commit**

`git add app/src/main/java/com/quranreader/custom/data/model/ArabicVerse.kt app/src/main/java/com/quranreader/custom/data/model/Transliteration.kt app/src/main/java/com/quranreader/custom/data/local/ArabicVerseDao.kt app/src/main/java/com/quranreader/custom/data/local/TransliterationDao.kt`
`git commit -m "feat(data): add ArabicVerse and Transliteration entities + DAOs"`

---

## Task 3: MIGRATION_9_10 + register DAOs

**Files:**
- Modify: `app/src/main/java/com/quranreader/custom/data/local/QuranDatabase.kt`

- [ ] **Step 1: Add entities to `@Database` and bump version**

Replace the `@Database(...)` annotation block:

```kotlin
@Database(
    entities = [
        Bookmark::class,
        AyahCoordinate::class,
        TranslationText::class,
        TranslationEdition::class,
        DownloadedSurah::class,
        AyahTiming::class,
        MemorizationSession::class,
        com.quranreader.custom.data.model.ArabicVerse::class,
        com.quranreader.custom.data.model.Transliteration::class,
    ],
    version = 10,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun ayahCoordinateDao(): AyahCoordinateDao
    abstract fun translationDao(): TranslationDao
    abstract fun translationEditionDao(): TranslationEditionDao
    abstract fun downloadedSurahDao(): DownloadedSurahDao
    abstract fun ayahTimingDao(): AyahTimingDao
    abstract fun memorizationSessionDao(): MemorizationSessionDao
    abstract fun arabicVerseDao(): com.quranreader.custom.data.local.ArabicVerseDao
    abstract fun transliterationDao(): com.quranreader.custom.data.local.TransliterationDao
```

- [ ] **Step 2: Add `MIGRATION_9_10` inside `companion object`**

```kotlin
        /**
         * v10: introduce per-verse Arabic text (`arabic_verses`) and
         * per-language transliteration (`transliterations`). Both
         * tables are content tables — keys are (surah, ayah) plus
         * languageCode for transliteration. Purely additive: zero
         * impact on user data already in v9 (bookmarks, sessions,
         * translations, etc.). The arabic_verses table is populated
         * post-migration by [ArabicVerseSeeder] from the bundled
         * `assets/quran_data/quran_uthmani.json`. Transliterations
         * are populated lazily by [TransliterationRepository] on the
         * first juz-read in Translation mode.
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
```

- [ ] **Step 3: Wire migration in DI module**

Find `AppModule.kt` Room provider; add `.addMigrations(..., MIGRATION_9_10)` to the existing migration chain. Exact line — locate the `Room.databaseBuilder(...)` call and append `MIGRATION_9_10` to the existing `addMigrations(...)` call (the chain currently includes `MIGRATION_3_4` … `MIGRATION_8_9`).

```kotlin
.addMigrations(
    QuranDatabase.MIGRATION_3_4,
    QuranDatabase.MIGRATION_4_5,
    QuranDatabase.MIGRATION_5_6,
    QuranDatabase.MIGRATION_6_7,
    QuranDatabase.MIGRATION_7_8,
    QuranDatabase.MIGRATION_8_9,
    QuranDatabase.MIGRATION_9_10,
)
```

- [ ] **Step 4: Provide the new DAOs**

Add to `AppModule.kt` (alongside existing DAO providers):

```kotlin
@Provides fun provideArabicVerseDao(db: QuranDatabase) = db.arabicVerseDao()
@Provides fun provideTransliterationDao(db: QuranDatabase) = db.transliterationDao()
```

- [ ] **Step 5: Build**

`./gradlew :app:assembleDebug` → exit 0. Room KSP will emit the new schemas.

- [ ] **Step 6: Commit**

`git add app/src/main/java/com/quranreader/custom/data/local/QuranDatabase.kt app/src/main/java/com/quranreader/custom/di/AppModule.kt`
`git commit -m "feat(db): bump schema to v10, add arabic_verses and transliterations tables"`

---

## Task 4: Bundle Arabic asset (fixture-first)

**Files:**
- Create: `app/src/main/assets/quran_data/quran_uthmani.json` (start as 3-verse fixture; production task to backfill all 6,236)
- Create: `app/src/main/assets/quran_data/LICENSE-tanzil.txt`

- [ ] **Step 1: Create the fixture asset**

`app/src/main/assets/quran_data/quran_uthmani.json`:

```json
[
  { "s": 1, "a": 1, "t": "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ" },
  { "s": 1, "a": 2, "t": "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ" },
  { "s": 1, "a": 3, "t": "ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ" }
]
```

- [ ] **Step 2: Create license notice**

`app/src/main/assets/quran_data/LICENSE-tanzil.txt`:

```
Quran Uthmani text in this asset bundle is sourced from the Tanzil
Project (https://tanzil.net) and is distributed under Tanzil terms.
Attribution is rendered in the app's About screen. Before shipping
to production, replace this fixture with the full 6,236-ayah file
generated from a Tanzil text export.
```

- [ ] **Step 3: Commit fixture**

`git add app/src/main/assets/quran_data/quran_uthmani.json app/src/main/assets/quran_data/LICENSE-tanzil.txt`
`git commit -m "feat(assets): add Arabic Uthmani fixture (3 verses) + Tanzil license"`

> **Production follow-up** (do NOT block on this — track as separate ticket): write `tools/build_uthmani_json.py` that pulls from a verified Tanzil source, validates the count == 6,236 against `ayahinfo.db`, and overwrites the fixture. Until then, Translation mode renders only Al-Fatihah verses 1–3 — useful for end-to-end UI smoke without licence drift.

---

## Task 5: ArabicVerseSeeder + first-launch wiring

**Files:**
- Create: `app/src/main/java/com/quranreader/custom/data/seed/ArabicVerseSeeder.kt`
- Create: `app/src/test/java/com/quranreader/custom/data/seed/ArabicVerseSeederTest.kt`
- Modify: `app/src/main/java/com/quranreader/custom/QuranReaderApplication.kt` (or whichever class is `@HiltAndroidApp`)

- [ ] **Step 1: Failing test**

```kotlin
package com.quranreader.custom.data.seed

import com.quranreader.custom.data.local.ArabicVerseDao
import com.quranreader.custom.data.model.ArabicVerse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicVerseSeederTest {
    private val captured = mutableListOf<ArabicVerse>()
    private val dao = object : ArabicVerseDao {
        override suspend fun getRange(surah: Int, from: Int, to: Int) = emptyList<ArabicVerse>()
        override suspend fun get(surah: Int, ayah: Int): ArabicVerse? = null
        override suspend fun count() = captured.size
        override suspend fun insertAll(verses: List<ArabicVerse>) { captured += verses }
    }

    @Test fun `parses three-verse fixture`() = runTest {
        val json = """[
            {"s":1,"a":1,"t":"بِسْمِ"},
            {"s":1,"a":2,"t":"ٱلْحَمْدُ"},
            {"s":1,"a":3,"t":"ٱلرَّحْمَـٰنِ"}
        ]""".trimIndent()
        ArabicVerseSeeder(dao).seedFromString(json)
        assertEquals(3, captured.size)
        assertEquals(1 to 1, captured[0].surahNumber to captured[0].ayahNumber)
        assertEquals("بِسْمِ", captured[0].textUthmani)
    }

    @Test fun `skips seed when already populated`() = runTest {
        captured += ArabicVerse(1, 1, "x")
        ArabicVerseSeeder(dao).seedFromString("""[{"s":2,"a":1,"t":"y"}]""")
        assertEquals(1, captured.size) // unchanged
    }
}
```

- [ ] **Step 2: Run, see fail**

`./gradlew :app:testDebugUnitTest --tests "com.quranreader.custom.data.seed.ArabicVerseSeederTest"` → FAIL (class missing).

- [ ] **Step 3: Implement seeder**

```kotlin
package com.quranreader.custom.data.seed

import android.content.Context
import com.quranreader.custom.data.local.ArabicVerseDao
import com.quranreader.custom.data.model.ArabicVerse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot import of bundled Uthmani Arabic text from
 * `assets/quran_data/quran_uthmani.json` into the `arabic_verses` Room
 * table. Idempotent: a non-zero row count short-circuits the read so
 * subsequent launches don't re-parse the JSON.
 */
@Singleton
class ArabicVerseSeeder @Inject constructor(
    private val dao: ArabicVerseDao,
) {
    /** App-launch entry point. Reads the bundled asset and seeds Room. */
    suspend fun seedFromAssets(context: Context) = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        seedFromString(json)
    }

    /** Test entry point and shared parser. Idempotent vs row count. */
    suspend fun seedFromString(json: String) {
        if (dao.count() > 0) return
        val arr = JSONArray(json)
        val batch = ArrayList<ArabicVerse>(BATCH_SIZE)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            batch += ArabicVerse(o.getInt("s"), o.getInt("a"), o.getString("t"))
            if (batch.size >= BATCH_SIZE) {
                dao.insertAll(batch); batch.clear()
            }
        }
        if (batch.isNotEmpty()) dao.insertAll(batch)
    }

    companion object {
        private const val ASSET_PATH = "quran_data/quran_uthmani.json"
        private const val BATCH_SIZE = 200
    }
}
```

- [ ] **Step 4: Run test, verify pass**

`./gradlew :app:testDebugUnitTest --tests "com.quranreader.custom.data.seed.ArabicVerseSeederTest"` → PASS.

- [ ] **Step 5: Wire into Application class**

Find the `@HiltAndroidApp` Application class (likely `QuranReaderApplication.kt`). Inject the seeder and trigger from `onCreate()`:

```kotlin
@Inject lateinit var arabicSeeder: com.quranreader.custom.data.seed.ArabicVerseSeeder

override fun onCreate() {
    super.onCreate()
    // ...existing init...
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try { arabicSeeder.seedFromAssets(this@QuranReaderApplication) } catch (_: Exception) {}
    }
}
```

> **Note**: per `android-coroutines` skill, `GlobalScope` is normally banned — but seeding-on-app-start is one of the documented exceptions (app-lifetime task tied to `Application.onCreate`). If an `applicationScope` is already injected elsewhere in the project, prefer it.

- [ ] **Step 6: Build + commit**

`./gradlew :app:assembleDebug` → exit 0.
`git add app/src/main/java/com/quranreader/custom/data/seed/ArabicVerseSeeder.kt app/src/test/java/com/quranreader/custom/data/seed/ArabicVerseSeederTest.kt app/src/main/java/com/quranreader/custom/QuranReaderApplication.kt`
`git commit -m "feat(data): seed arabic_verses from bundled JSON on app start"`

---
