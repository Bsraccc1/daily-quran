# Changelog

## v1.0.0 — Initial release

First public release of Daily Quran. Bundled Mushaf, offline reading, sessions, bookmarks, translations, audio recitation with highlight sync, and Hifz memorization mode.

### Highlights

**Bundled Mushaf reader**
- 604 Madinah pages shipped as transparent-background WebP, 1024×1656, ~53 MB total.
- `MushafImageRenderer` tints the calligraphy with `MaterialTheme.colorScheme.onSurface` via `ColorFilter.tint(SrcIn)`, so light, dark, and dynamic-colour themes all render correctly with no runtime download.
- `ayahinfo.db` (Room `createFromAsset`) maps every glyph rectangle in the 1024×1656 image space to its `(surah, ayah, line)`, enabling long-press tap detection and per-line highlighting.

**Reading sessions**
- Set a target page count, track progress, and resume across launches via DataStore.
- "Session complete" bottom sheet with continue / close, plus dashboard handoff to the Mushaf reader.

**Bookmarks**
- Page-level bookmarks with optional notes, persisted in Room.
- One-tap toggle from the reader info panel; scale-on-press micro-interaction.

**Translations**
- Sahih International (English) and Indonesian Ministry of Religious Affairs (Bahasa Indonesia), downloaded once and cached in `translations` Room table.

**Audio recitation**
- Media3 ExoPlayer 1.3.1 with `SimpleCache` + `CacheDataSource` for offline replay.
- `AudioDownloadWorker` (HiltWorker + WorkManager) pre-warms the cache per surah with HTTP `Range` resume support.
- `HighlightSyncEngine` polls the player position and emits the active `AyahKey` based on cached timing data from the quran.com API; falls back to MediaItem-index granularity when timings are missing.

**Memorization (Hifz) mode**
- ModalBottomSheet overlay with repeat targets (3 / 5 / 10 / 20), auto-advance, transport controls, and a dedicated ExoPlayer for `Player.REPEAT_MODE_ONE` looping.
- Sessions persisted in `memorization_sessions` (range, repeats target/completed, total seconds, auto-advance, started/completed timestamps); `MemorizationHistoryScreen` lists past sessions with resume-by-tap and delete.

**UI motion system**
- `ui/theme/Motion.kt` exposes Material 3 emphasized + standard easing/duration tokens (`short()`, `standard()`, `emphasized()`).
- Reusable `ui/components/animated/` primitives: `AnimatedCard`, `ChipRow`, `ExpandableSection`, `AnimatedCounterRing`, `SharedElementContainer`.

**Themes**
- Five colour palettes (Zamrud Islami, Teal & Dusk, Amber Masjid, Indigo Malam, Material You), each with light and dark variants.
- Material You falls back gracefully to Zamrud Islami below Android 12.

**Bilingual UI**
- English (`values/strings.xml`) and Bahasa Indonesia (`values-in/strings.xml`).
- `LocaleManager.applyLocale` runs in `QuranReaderApp.onCreate` so the saved language is applied before any UI inflates.

**Home-screen widget**
- `QuranWidgetProvider` shows reading progress; updates on app foreground or AppWidget refresh broadcast.
