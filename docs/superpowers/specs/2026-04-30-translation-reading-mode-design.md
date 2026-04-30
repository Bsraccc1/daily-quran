# Translation Reading Mode — Design

> **Status**: Draft, pending user review
> **Date**: 2026-04-30
> **Author**: AI pair (via brainstorming skill)
> **Module**: `:app` (single-module repo per `AGENTS.md`)

---

## 1. Goal

Add a second reading style — **Translation mode** — that complements the existing
**Mushaf mode** (page-by-page WebP renderer). Users pick their default in
Settings → Reading → Reading Mode, and can flip between modes mid-read via a
top-bar toggle in either reader. Position and bookmarks sync across modes.

### What "Translation mode" looks like

Reference screenshot supplied by user: per-Juz scrollable list of verse cards.
Each card shows:

- **Hizb / surah marker** (when relevant)
- **Arabic ayah** (Uthmanic font, RTL, with end-of-ayah glyph)
- **Italic transliteration** (Latin script with macrons)
- **Translation paragraph** (current edition, e.g. Indonesian Ministry)
- **Action row**: ayah-number badge → play → bookmark → share → jump-to-mushaf

A `ScrollableTabRow` of 30 Juz tabs sits pinned to the top.

---

## 2. Locked-in design decisions

Captured from the brainstorming session (4 questions, 4 answers):

| # | Decision | Choice |
|---|----------|--------|
| 1 | Navigation unit | **Per-Juz scroll** — pick a juz, scroll all verses in it; juz tabs swap juz |
| 2 | Per-verse content | **Arabic + transliteration + translation + action row** (full screenshot) |
| 3 | Cross-mode position | **Sync both ways** — last-position is `(surah, ayah)`, translates to/from mushaf page using `ayahinfo.db` |
| 4 | Toggle UX | **Settings entry + top-bar icon in both readers** |

### Data sourcing (user decision)

- **Arabic verse text**: bundled in `app/src/main/assets/quran_data/quran_uthmani.json` (Tanzil-licensed Uthmani text, public-domain script). Imported into Room on first launch via `MIGRATION_9_10` + asset seeder.
- **Translation**: existing `TranslationRepository` flow (quran.com `/translations/{id}`). No change.
- **Transliteration**: fetched from quran.com on demand and cached in Room — same fetch+cache pattern as translation, mirrored in a new `TransliterationRepository`.

---

## 3. Non-goals

- Word-by-word translation (separate future feature; covered by `word_alignment.db` we don't read yet).
- Tafsir display (out of scope).
- Multi-juz scrolling (each juz is its own pager page).
- Splitting `:app` into multiple Gradle modules (`AGENTS.md` forbids).
- Replacing the WebP-based Mushaf renderer (Mushaf mode unchanged).
- A standalone "Surah scroll" mode (user picked Per-Juz; Surah list still navigates by surah-start-page in Mushaf mode).

---

## 4. Architecture overview

```
┌─────────────────────── UI ────────────────────────┐
│  SettingsScreen ──► ReadingMode chip              │
│                                                    │
│  Dashboard / Juz / Bookmarks                       │
│       │                                            │
│       ▼ (mode-aware navigation)                    │
│  ┌─ MushafReaderScreen (existing) ─┐               │
│  │  top-bar [SwapHoriz] ───────────┼──► flip ──┐   │
│  └────────────────────────────────┘           │   │
│  ┌─ TranslationReaderScreen (NEW) ─┐          │   │
│  │  top-bar [SwapHoriz] ───────────┼──► flip ─┘   │
│  │  ScrollableTabRow(30 juz)                       │
│  │  HorizontalPager ──► JuzVerseList               │
│  │     ├─ SurahHeader                              │
│  │     ├─ VerseCard (Arabic / translit / trans)    │
│  │     └─ ActionRow                                │
│  └─────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────┘
                           │
┌────────────────── Domain ──────────────────────────┐
│  TranslationReaderViewModel  PositionMapper        │
│  ReaderModeRepository (preference + last-position) │
└────────────────────────────────────────────────────┘
                           │
┌─────────────────── Data ───────────────────────────┐
│  ArabicVerseDao   (NEW Room table, seeded asset)   │
│  TransliterationDao (NEW Room table, fetch+cache)  │
│  TranslationDao   (existing)                       │
│  AyahInfoRepository (existing — page ↔ surah/ayah) │
│  BookmarkDao / SessionDao (existing, no change)    │
└────────────────────────────────────────────────────┘
```

---

## 5. Data layer

### 5.1 Room schema — `MIGRATION_9_10`

Two new tables, both keyed by `(surahNumber, ayahNumber)`. No changes to existing tables.

```sql
CREATE TABLE IF NOT EXISTS arabic_verses (
    surahNumber INTEGER NOT NULL,
    ayahNumber  INTEGER NOT NULL,
    textUthmani TEXT NOT NULL,
    PRIMARY KEY (surahNumber, ayahNumber)
);

CREATE TABLE IF NOT EXISTS transliterations (
    surahNumber INTEGER NOT NULL,
    ayahNumber  INTEGER NOT NULL,
    languageCode TEXT NOT NULL DEFAULT 'en',
    text TEXT NOT NULL,
    PRIMARY KEY (surahNumber, ayahNumber, languageCode)
);
```

Bumps `QuranDatabase.version` from **9 → 10**. Migration is purely additive,
so every existing row (bookmarks, sessions, translations, downloads,
ayah-timings, memorization) carries forward unchanged.

### 5.2 Asset bundle

- `app/src/main/assets/quran_data/quran_uthmani.json` — array of 6,236 entries `{ s: Int, a: Int, t: String }`. Source: Tanzil "uthmani" plain text, validated against ayahinfo.db ayah counts. Estimated ~600 KB raw, ~200 KB compressed (AAB applies asset compression).
- License: Tanzil terms (free for non-commercial / commercial w/ attribution). License notice added to **About** card in Settings (already lists Mushaf Madinah attribution — same pattern).

### 5.3 First-launch seeder

`ArabicVerseSeeder` runs in `MIGRATION_9_10`:

1. Read `quran_uthmani.json` from assets.
2. Insert into `arabic_verses` in batches of 200 (mirrors `TranslationRepository.downloadEdition` batching).
3. Wrapped in `try/catch` — on failure, log and continue (Mushaf mode still works without Arabic text; Translation mode shows a "Quran text unavailable" empty state).

### 5.4 Repositories

- **`ArabicVerseRepository`** (new, `@Singleton`): `getRange(surah, fromAyah, toAyah)`, `getJuz(juz: Int): List<ArabicVerse>` (uses `QuranInfo` for juz boundaries). Pure DB reads, no network.
- **`TransliterationRepository`** (new, `@Singleton`): mirrors `TranslationRepository` shape. `getTransliterationsForRange(...)`, `downloadAll(languageCode)`. Backed by quran.com — endpoint TBD during plan (likely `/transliterations` or `/verses?fields=transliteration`).
- **`TranslationRepository`** (existing): no API surface change. We'll add a `getRange(surah, fromAyah, toAyah, editionId)` method that already exists — verified at line 14 of `TranslationDao.kt`.

---

## 6. Domain layer

### 6.1 `ReadingMode` enum

```kotlin
// data/preferences/UserPreferences.kt — alongside DisplayMode, AutoSaveMode
enum class ReadingMode { MUSHAF, TRANSLATION }
```

### 6.2 `PositionMapper`

Pure-Kotlin utility (no Android imports — testable on JVM). Wraps existing
`AyahInfoRepository` and `QuranInfo`.

```kotlin
class PositionMapper @Inject constructor(
    private val ayahInfo: AyahInfoRepository
) {
    /** Returns the (surah, ayah) of the first ayah on [page]. */
    suspend fun pageToSurahAyah(page: Int): SurahAyah?

    /** Returns the mushaf page that contains (surah, ayah). */
    suspend fun surahAyahToPage(surah: Int, ayah: Int): Int

    /** Returns the juz number (1..30) containing (surah, ayah). */
    fun surahAyahToJuz(surah: Int, ayah: Int): Int = QuranInfo.juzOf(surah, ayah)
}

data class SurahAyah(val surah: Int, val ayah: Int)
```

`pageToSurahAyah` uses the bundled `ayahinfo.db` glyph table — same source
`TranslationRepository.ayahsOnPage` already uses (`AyahInfoRepository.glyphsForPage`).
`QuranInfo.juzOf` will be added if it doesn't exist (the file is ~14 KB, we'll
verify in the plan; otherwise we add a static juz boundary lookup).

### 6.3 `TranslationReaderViewModel`

```kotlin
@HiltViewModel
class TranslationReaderViewModel @Inject constructor(
    private val arabicRepo: ArabicVerseRepository,
    private val transliterationRepo: TransliterationRepository,
    private val translationRepo: TranslationRepository,
    private val userPrefs: UserPreferences,
    private val audioVm: AudioViewModel,                  // shared singleton
    savedState: SavedStateHandle,
) : ViewModel() {

    data class VerseRow(
        val surah: Int,
        val ayah: Int,
        val arabic: String,
        val transliteration: String?,    // null until first fetch completes
        val translation: String?,
        val isHizbStart: Boolean,
        val isSurahStart: Boolean,
    )

    data class JuzUiState(
        val juz: Int,
        val rows: List<VerseRow>,
        val isLoading: Boolean,
        val error: String? = null,
    )

    val currentJuz: StateFlow<Int>           // initial = arg or last-position derived
    val juzState: StateFlow<JuzUiState>

    fun selectJuz(juz: Int)
    fun playAyah(surah: Int, ayah: Int)
    fun toggleBookmark(surah: Int, ayah: Int)
    fun shareAyah(surah: Int, ayah: Int)
    fun jumpToMushaf(surah: Int, ayah: Int)  // emits navigation event via SharedFlow
}
```

Follows the `android-viewmodel` skill: read-only `StateFlow` for state,
`MutableSharedFlow(replay = 0)` for one-off navigation events.

### 6.4 `ReaderModeRepository`

Thin wrapper around `UserPreferences` that exposes:

```kotlin
val readingMode: Flow<ReadingMode>
suspend fun setReadingMode(mode: ReadingMode)

val lastPosition: Flow<SurahAyah>          // single source of truth
suspend fun setLastPosition(surah: Int, ayah: Int)
```

`lastPosition` is derived: when in Mushaf mode and the user flips to page X,
the existing reader will (additionally) write `lastPosition = pageToSurahAyah(X)`.
When in Translation mode, the reader writes `lastPosition` as the user scrolls
past each ayah's anchor (debounced with the existing auto-save dwell timer).

---

## 7. UI layer

### 7.1 Settings — new "Reading Mode" entry

Inside the existing **Reading** card in `SettingsScreen.kt` (currently houses
"Pages per Continue Reading" and the Auto-save section). Added at the **top**
of the Reading card, above the existing inputs:

```kotlin
Text("Reading Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
Spacer(Modifier.height(8.dp))
Text("How verses are presented when you open the reader",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
Spacer(Modifier.height(12.dp))

Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
        selected = readingMode == ReadingMode.MUSHAF,
        onClick  = { vm.setReadingMode(ReadingMode.MUSHAF) },
        label    = { Text(stringResource(R.string.settings_reading_mode_mushaf)) },
        leadingIcon = if (readingMode == ReadingMode.MUSHAF) checkLeading() else null,
    )
    FilterChip(
        selected = readingMode == ReadingMode.TRANSLATION,
        onClick  = { vm.setReadingMode(ReadingMode.TRANSLATION) },
        label    = { Text(stringResource(R.string.settings_reading_mode_translation)) },
        leadingIcon = if (readingMode == ReadingMode.TRANSLATION) checkLeading() else null,
    )
}
Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))
```

Pattern mirrors the existing `AutoSaveMode` chips — same FilterChip + Check
leading icon + spacing. No new component required.

### 7.2 `TranslationReaderScreen` (new file)

`app/src/main/java/com/quranreader/custom/ui/screens/reading/TranslationReaderScreen.kt`

Structure:

```
Scaffold(
    topBar = { TranslationTopBar(surahName, mode, onToggleMode, onBack) },
)
└─ Column
   ├─ SurahHeaderPill(currentSurahName)  // floating, animates as user scrolls
   ├─ ScrollableTabRow(30 juz tabs, selected = currentJuz)
   └─ HorizontalPager(pageCount = 30, state)
       └─ LazyColumn(per-juz)
           ├─ SurahHeader (sticky for first surah of each juz)
           ├─ items(verses, key = { "${it.surah}:${it.ayah}" }) { VerseCard(it) }
           └─ HizbDivider (every 1/4 hizb boundary)
```

`VerseCard` Composable (matches screenshot):

```kotlin
@Composable
fun VerseCard(row: VerseRow, onAction: (Action) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (row.isHizbStart) HizbMarker()
        Text(
            text = row.arabic,
            fontFamily = MushafFonts.Uthmanic,
            fontSize = 28.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (row.transliteration != null) {
            Text(
                row.transliteration,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            row.translation ?: stringResource(R.string.translation_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        ActionRow(row, onAction)
        Spacer(Modifier.height(8.dp))
        DiamondDivider()
    }
}
```

Constraints from `compose-ui` and `compose-performance-audit` skills:
- `LazyColumn` items keyed by `"surah:ayah"` (stable identity).
- Lambdas remembered with `viewModel::onAction`.
- `VerseRow` is `@Immutable` (all `val`, all stable types).
- Arabic font loaded once via existing `MushafFonts.Uthmanic` (no re-creation per row).

### 7.3 Top-bar toggle (both readers)

Both `MushafReaderScreen` and `TranslationReaderScreen` get a top-bar IconButton:

```kotlin
IconButton(onClick = onToggleMode) {
    Icon(
        imageVector = Icons.Default.SwapHoriz,
        contentDescription = stringResource(R.string.reader_toggle_mode),
    )
}
```

Tap behavior:
1. Capture current `(surah, ayah)`:
   - Mushaf: `pageToSurahAyah(currentPage)`.
   - Translation: first visible ayah in `LazyListState`.
2. `userPrefs.setReadingMode(other)`.
3. Navigate to the other reader, passing `(surah, ayah)` as nav args.
4. Target reader uses arg as `initialPosition`; existing autoscroll/initial-page
   logic in `MushafReaderScreen` already supports this (the `surah, ayah` args
   on `Screen.MushafReader` route).

### 7.4 Mode-aware entry points

`NavGraph.kt` currently sends every "open reader" CTA to `Screen.MushafReader`.
We add a small router:

```kotlin
private fun NavController.openReader(
    page: Int, surah: Int? = null, ayah: Int? = null, mode: ReadingMode,
) {
    val route = when (mode) {
        ReadingMode.MUSHAF      -> Screen.MushafReader.createRoute(page, surah, ayah)
        ReadingMode.TRANSLATION -> Screen.TranslationReader.createRoute(
                                       juz = QuranInfo.juzOf(surah ?: 1, ayah ?: 1),
                                       initialSurah = surah, initialAyah = ayah)
    }
    navigate(route) { launchSingleTop = true }
}
```

`Dashboard`, `Juz`, `Bookmarks` callsites become 1-line edits — they already
have `surah` / `ayah` from search results / bookmark rows.

---

## 8. Cross-mode position sync

- Single source of truth: `UserPreferences.lastSurahAyah` (new int×2 keys
  `LAST_SURAH_KEY`, `LAST_AYAH_KEY`, default 1:1).
- Existing `LAST_PAGE_KEY` stays — still used by Mushaf reader for resume.
  When the user flips a page in Mushaf mode, both `LAST_PAGE_KEY` and
  `LAST_SURAH_KEY/LAST_AYAH_KEY` are written.
- Translation mode writes only `LAST_SURAH_KEY/LAST_AYAH_KEY` (plus implicitly
  derives `LAST_PAGE_KEY` for the Continue Reading CTA on Dashboard).
- Bookmarks: already keyed `(surah, ayah)` — render in both modes unchanged.
- Sessions: keyed by mushaf page. Translation mode contributes by translating
  `(surah, ayah) → page` at session-tick time. Same `AutoSaveMode` mechanics.
- Memorization: keyed `(surah, ayah)` already — works in both modes.

---

## 9. Audio integration

Per `AGENTS.md`: "all playback goes through `AudioService` (Media3
`MediaSessionService`) and the `AudioCacheManager`". `TranslationReaderViewModel`
exposes `playAyah(surah, ayah)` which delegates to the existing shared
`AudioViewModel.playAyah(surah, ayah)`. No new player instance.

Per-ayah playback already exists in Mushaf reader — we reuse the call site verbatim.

---

## 10. Theming

All five themes (Zamrud, Teal, Amber, Indigo, Material You) bind to
`MaterialTheme.colorScheme`:

| Element | Token |
|---------|-------|
| Ayah-number badge background | `colorScheme.primary` |
| Ayah-number text | `colorScheme.onPrimary` |
| Hizb marker (filled circle) | `colorScheme.primary` |
| Diamond divider | `colorScheme.outlineVariant` |
| Italic transliteration | `colorScheme.primary` |
| Surah-header pill background | `colorScheme.surfaceVariant` |
| Action-row icons | `colorScheme.onSurfaceVariant` |
| Selected juz tab indicator | `colorScheme.primary` |

No `Color(0xFF...)` literals in Composables. Per `mobile-android-design` skill.

---

## 11. Internationalisation

Per `AGENTS.md`: every visible string added to **both** `res/values/strings.xml`
(en) and `res/values-in/strings.xml` (id).

New strings:

| Key | en | id |
|-----|----|----|
| `settings_reading_mode_title` | Reading Style | Gaya Membaca |
| `settings_reading_mode_subtitle` | How verses are presented when you open the reader | Cara ayat ditampilkan saat Anda membuka pembaca |
| `settings_reading_mode_mushaf` | Mushaf | Mushaf |
| `settings_reading_mode_translation` | Translation | Terjemahan |
| `reader_toggle_mode` | Switch reading style | Ganti gaya membaca |
| `translation_loading` | Loading translation… | Memuat terjemahan… |
| `transliteration_loading` | Loading transliteration… | Memuat transliterasi… |
| `translation_mode_juz_label` | Juz %d | Juz %d |
| `translation_mode_empty` | Quran text unavailable. Try clearing cache and reopening. | Teks Quran tidak tersedia. Coba bersihkan cache dan buka ulang. |
| Tanzil licence notice | Arabic text licensed under Tanzil terms (tanzil.net) | Teks Arab dilisensikan di bawah ketentuan Tanzil (tanzil.net) |

Ayah-numbering uses the Latin digits already used elsewhere (no new locale-specific digit substitution).

---

## 12. Testing strategy

Per `android-testing` and `android-testing-unit` skills.

### Unit tests (JVM, fast)
- `PositionMapperTest` — round-trip `pageToSurahAyah` ↔ `surahAyahToPage` for each surah-start page; verify edge of juz boundaries (1, 30).
- `ArabicVerseSeederTest` — fixture JSON of 3 ayahs, verify all rows inserted with correct PK.
- `TranslationReaderViewModelTest` — fake repos, verify `selectJuz` updates state, transliteration null-safety, debounce behaviour for last-position write.
- `ReadingModeRepositoryTest` — preference round-trip, default-on-first-launch.

### Hilt integration tests (instrumented)
- `MIGRATION_9_10` real-database test (mirrors existing `MIGRATION_8_9` test if present).
- Asset-seeder loads the bundled file, count == 6,236.

### Roborazzi screenshot tests (existing pattern in `android-testing`)
- `VerseCard` in 5 themes × light/dark.
- `TranslationReaderScreen` first-juz, mid-scroll.

### Manual smoke
- Toggle in settings → both readers respect.
- Top-bar toggle preserves position.
- Bookmark in Mushaf → visible in Translation. And vice versa.

---

## 13. Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| Tanzil licence misuse | Add attribution to **About** card; verify "uthmani" specific text is permissively licensed before the asset goes in. Plan task explicitly checks the licence file. |
| 200 KB asset growth angers users on cheap devices | App is already ~30 MB (mushaf pages dominate). 200 KB is <1% growth — acceptable. |
| quran.com transliteration endpoint missing the languages we need | Plan task verifies endpoint; fallback: bundle a single English transliteration JSON in v1, lazy-fetch others later. |
| `ayahinfo.db` glyph row missing for some pages | Existing `TranslationRepository` already has a 3-tier fallback (`ayahinfo.db` → legacy `ayah_coordinates` → coarse estimate). `PositionMapper` reuses the same fallback chain. |
| `MIGRATION_9_10` failure on user devices | Migration is purely additive — `CREATE TABLE IF NOT EXISTS`. Worst case: tables don't get populated; Translation mode shows empty state. Mushaf mode unaffected. |
| Performance: 6,236 rows in `LazyColumn` if user scrolls a long juz | `LazyColumn` only composes visible rows. We load **per-juz** (≈200 verses), keys are stable, items are `@Immutable` — recomposition cost is bounded. |

---

## 14. Open questions resolved during brainstorming

(none remain)

---

## 15. References

- Mushaf reader: `app/src/main/java/com/quranreader/custom/ui/screens/reading/MushafReaderScreen.kt`
- Settings screen: `app/src/main/java/com/quranreader/custom/ui/screens/settings/SettingsScreen.kt`
- User prefs: `app/src/main/java/com/quranreader/custom/data/preferences/UserPreferences.kt`
- Existing migrations: `app/src/main/java/com/quranreader/custom/data/local/QuranDatabase.kt`
- Translation repo (pattern reference): `app/src/main/java/com/quranreader/custom/data/repository/TranslationRepository.kt`
- Ayah-info glyph table: `app/src/main/java/com/quranreader/custom/data/local/ayahinfo/AyahInfoDatabase.kt`
- Audio service: `AudioService` + `AudioCacheManager` (per `AGENTS.md`)
- Themes: 5 themes per `AGENTS.md` (Zamrud, Teal, Amber, Indigo, Material You)
- Reference screenshot: provided 2026-04-30 by user

---

## 16. Self-review checks (run by author before user review)

- [x] No "TBD" / "TODO" left in core sections (only the licence verification, which is a plan task — explicit).
- [x] Migration is additive (`CREATE TABLE IF NOT EXISTS`); no destructive change to existing tables.
- [x] Every visible string has en + id entries.
- [x] No new gradle module proposed.
- [x] Audio routed through existing `AudioService` (no raw `ExoPlayer`).
- [x] Theme colours bind to `MaterialTheme.colorScheme` (no baked literals).
- [x] No RxJava introduced.
- [x] No `fallbackToDestructiveMigration`.
- [x] Hilt-only DI; no Koin / Dagger-without-Hilt.
- [x] Single-module preserved.
- [x] Type-consistency check: `ReadingMode` referenced consistently (not `ReadingStyle`); `SurahAyah` data class used everywhere across mapper / VM / repo.
