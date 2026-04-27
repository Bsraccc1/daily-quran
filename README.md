# Daily Quran

Modern Android Quran reader built with Jetpack Compose and Material 3. Bundles all 604 Mushaf Madinah pages, an offline ayah-coordinate database for tap-to-highlight, and streams recitations on demand.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-FF6F00.svg)](https://m3.material.io/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21-yellow.svg)](https://developer.android.com/about/versions/lollipop)

---

To Do :
- Add translations for each verse
- Display only the translation of the currently highlighted verse; for the translation download interface, activate it when the translation button is clicked, and display a complete list of available translations from the API, such as from quran.com and others
- The translation interface should not cover the entire screen, but rather appear as a vertical slider
- Add an option to display only the highlighted verse, or all verses
- Translate on every page; if a page starts at 1 and ends at 7, display only 1–7 if the user selects all verses,
- if the user selects verse 6, display only the translation of verse 6
- Add more reciters to the audio
- New Quran layout type
- Ensure all user interfaces function across all phone screen resolutions (DPI)
- Add home page session management, which can be based on a specific juz or page
- Add landscape mode support; when the phone is in landscape mode, the Quran reader will be zoomed in for easier reading
- Replace all Mac blur effects in the source code with Gaussian Blur for better aesthetics
- Users can switch surahs via the swipe-down interface in the Quran reader
- Add landscape and portrait orientation toggle buttons in the swipe-up interface in the Quran reader
- Add distinct button interfaces in the swipe-down interface for navigation option 1 (surah and verse) and option 2 (page)
- Updated the screenshot preview in readme.md

Known bugs:
- Audio download interface: after clicking “Browse Surah,” users cannot click “Settings” again
- Session progress updates only occur when clicking “Continue Reading,” not when actually switching to the next page to update progress in session management
  (consider adding a delay of about 5 seconds to ensure the user has scrolled and is reading on the new page)
- User interface (UI) positioning is not optimal on other devices such as phones, etc.
- UI positioning is poor on other devices, such as phones.


Translated with DeepL.com (free version)
## Old Screenshots (not updated screenshot)

### Mushaf Reader

| Page 1 — Al-Fatihah (light) | Page 2 — Al-Baqarah (light) | Page 1 — Dark mode |
| --- | --- | --- |
| ![Mushaf page 1](screenshots/06_mushaf.png) | ![Mushaf page 2](screenshots/08_mushaf_p2.png) | ![Mushaf dark](screenshots/10_mushaf_dark.png) |

The Mushaf renderer uses bundled transparent-background WebP images tinted at runtime, so the calligraphy follows the active theme without any "white card" flashing in dark mode.

### Reader info panel

Single-tap a page to reveal the floating control bar — back, translation, memorize, page number, audio playback, and bookmark.

| Light info panel | Dark info panel |
| --- | --- |
| ![Info panel light](screenshots/07_mushaf_panel.png) | ![Info panel dark](screenshots/11_mushaf_panel_dark.png) |

### Navigation tabs

| Reading dashboard | Juz / Surah / Hizb | Sessions | Bookmarks | Settings |
| --- | --- | --- | --- | --- |
| ![Dashboard](screenshots/01_dashboard.png) | ![Juz](screenshots/02_juz.png) | ![Session](screenshots/03_session.png) | ![Bookmarks](screenshots/04_bookmarks.png) | ![Settings](screenshots/05_settings.png) |

### Theme picker and search

| Theme picker (dark) | Search |
| --- | --- |
| ![Theme picker](screenshots/05_settings_dark.png) | ![Search](screenshots/12_search.png) |

---

## Features

- **Offline Mushaf reader** — 604 Madinah pages bundled as transparent WebP, ~53 MB, tinted with the active Material 3 colour scheme.
- **Tap-to-highlight ayah** — long-press anywhere on a page to highlight the matching ayah using the bundled `ayahinfo.db` glyph rectangles.
- **Reading sessions** — set a page target, track progress, finish or extend; the dashboard surfaces the active session and overall progress (page X of 604).
- **Bookmarks** — page-level bookmarks with optional notes; one-tap toggle from the reader info panel.
- **Three navigation tabs** — Juz (30), Surah (114) with Makki/Madani classification, and Hizb (60).
- **Translation overlay** — Sahih International (English) and Indonesian Ministry of Religious Affairs (Bahasa Indonesia), downloaded once and cached in Room.
- **Streaming recitation** — Media3 ExoPlayer with on-disk cache (`SimpleCache` + `CacheDataSource`), per-surah download manager, ayah-level highlight sync from the quran.com timing API.
- **Memorization (Hifz) mode** — overlay with repeat targets (3 / 5 / 10 / 20), persisted history, looping playback through the dedicated ExoPlayer.
- **Five themes** — Zamrud Islami, Teal & Dusk, Amber Masjid, Indigo Malam, and Material You (Android 12+), each with light and dark variants.
- **Bilingual UI** — English and Bahasa Indonesia, switchable from Settings without an app restart on most screens.
- **Home-screen widget** — `QuranWidgetProvider` shows reading progress at a glance.

---

## Tech stack

| Layer | Library |
| --- | --- |
| Language | Kotlin (100%) |
| UI | Jetpack Compose, Material 3, `material3-window-size-class` |
| Architecture | MVVM + repositories, Hilt DI |
| Local data | Room v8 (bookmarks, ayah coordinates, translations, audio downloads, ayah timings, memorization sessions) |
| Bundled DB | `ayahinfo.db` (per-glyph rectangles for tap detection), 1024×1656 reference image space |
| Async | Kotlin Coroutines + Flow |
| Storage | DataStore Preferences |
| Networking | Retrofit + OkHttp |
| Image loading | Coil 2.5 |
| Audio | Media3 ExoPlayer 1.3.1 with `media3-datasource` cache |
| Background | WorkManager + Hilt Work |
| Min / target SDK | 21 / 34 |

---

## Project layout

```
app/src/main/
├── assets/
│   ├── mushaf_pages/            # 604 transparent WebP pages
│   ├── quran_data/ayahinfo.db   # per-glyph pixel rectangles
│   ├── word_alignment.db        # word-level alignment for highlight sync
│   └── *.otf / *.ttf            # Uthmanic Hafs, Uthman Naskh, Kitab, OpenDyslexic
├── java/com/quranreader/custom/
│   ├── data/
│   │   ├── audio/               # Media3 service, cache, timing API, sync engine
│   │   ├── local/               # Room DB, DAOs, ayahinfo DB wrapper
│   │   ├── memorization/        # hifz repository + DAO
│   │   ├── model/               # Bookmark, AyahCoordinate, TranslationText, …
│   │   ├── preferences/         # DataStore + Language enum
│   │   ├── reminder/            # DailyVerseWorker
│   │   ├── repository/          # QuranRepository, BookmarkRepository, TranslationRepository
│   │   └── widget/              # QuranWidgetProvider
│   ├── di/                      # AppModule, AyahInfoModule
│   ├── ui/
│   │   ├── components/
│   │   │   ├── animated/        # AnimatedCard, ChipRow, ExpandableSection, …
│   │   │   └── mushaf/          # MushafImageRenderer, MushafColorTheme, MushafFonts
│   │   ├── navigation/NavGraph.kt
│   │   ├── screens/             # bookmarks, downloads, home, juz, memorization,
│   │   │                        # onboarding, reading, search, session, settings
│   │   ├── theme/               # Color, Type, Theme, Dimensions, Motion
│   │   └── viewmodel/           # 11 ViewModels
│   ├── util/ + utils/           # ViewModelExtensions, LocaleManager
│   └── QuranReaderApp.kt        # @HiltAndroidApp, applies saved locale
└── res/                         # strings (en, in), drawable, font, layout, xml
tools/
├── build_mushaf_pages.py        # one-off pipeline to fetch + encode page WebPs
└── build_quran_db.py            # legacy text DB builder (kept for reference)
```

---

## Build

Requirements: Android SDK 34, JDK 17, Gradle wrapper bundled.

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (R8 enabled)
./gradlew assembleRelease

# Install on connected device / emulator
./gradlew installDebug
```

Common dev commands:

```bash
./gradlew clean                  # remove build outputs
./gradlew :app:lint              # Android lint
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests on a device
```

The mushaf page assets and `ayahinfo.db` are committed to the repo, so a fresh clone builds and runs without any extra download step.

---

## How it works

### Mushaf rendering

`MushafImageRenderer` lays out a `BoxWithConstraints` whose aspect ratio matches the source image (1024×1656). The page is loaded with Coil's `AsyncImage`, drawn with `ContentScale.FillBounds`, and tinted via `ColorFilter.tint(theme.text, BlendMode.SrcIn)`. Because the image is alpha-only (transparent background, ink in the alpha channel), `SrcIn` paints the calligraphy in the theme's text colour over a `theme.background`-coloured `Box`.

`MushafImagePageViewModel` queries the bundled `ayahinfo.db` (Room's `createFromAsset`) for the page's `GlyphEntity` rows. On long press, `hitTest` divides the touch offset by the runtime scale factors and returns the first glyph whose pixel rectangle contains the point — that surfaces the ayah's `(surah, ayah)` to the parent screen, which then shows the translation sheet or audio popup.

When audio is playing, the highlight switches to an audio-driven `HighlightedAyah` (computed from `currentAudioAyah`), so the highlighted line follows the recitation rather than the last tap.

### Reading sessions

`ReadingViewModel` owns a single `SessionState` machine — `IDLE`, `INPUT_PENDING`, `ACTIVE`, `COMPLETE` — persisted in DataStore so the dashboard can resume the active session across launches. The Mushaf reader observes session state to auto-show the "Session complete" sheet when `pagesReadInSession >= sessionTargetPages`.

### Audio

`AudioService` is a Media3 `MediaSessionService` that builds an `ExoPlayer` whose `MediaSourceFactory` is wrapped by `AudioCacheManager` (a `CacheDataSource.Factory` over a `SimpleCache` rooted at `cacheDir/audio`). Surah downloads pre-warm the same cache through `AudioDownloadWorker`, so an offline-cached surah plays back immediately with no network hit.

`HighlightSyncEngine` polls the player's position and emits the active `AyahKey` based on `AyahTimingRepository` rows fetched from the quran.com timing API and persisted in Room.

---

## Permissions

Declared in `AndroidManifest.xml`:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — translation download, audio streaming, timing API
- `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Media3 audio service
- `FOREGROUND_SERVICE_DATA_SYNC` — WorkManager surah downloads
- `POST_NOTIFICATIONS` — playback + download progress (Android 13+)
- `READ_MEDIA_AUDIO` — playing locally cached recitations on Android 13+
- `MODIFY_AUDIO_SETTINGS`, `VIBRATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — playback polish

The app does not request location, contacts, camera, or any analytics SDK permissions.

---

## License

MIT — see [LICENSE](LICENSE).

The Mushaf Madinah page images and the `ayahinfo.db` glyph database are derived from publicly available [Quran.com](https://quran.com) / [quran_android](https://github.com/quran/quran_android) assets. Recitation audio is streamed from the [QuranicAudio](https://quranicaudio.com/) and [everyayah.com](https://everyayah.com/) CDNs.
