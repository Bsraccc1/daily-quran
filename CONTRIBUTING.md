# Contributing to Daily Quran

Thanks for taking the time to contribute. This document covers the workflow for filing issues, sending pull requests, and the conventions we follow in this codebase.

## Filing issues

Open a GitHub issue with:

- A clear title and short description.
- Reproduction steps for bugs (Android version, device model, app version).
- Expected vs actual behaviour, with a screenshot or screen recording when relevant.
- For feature requests, the use case and any alternatives you've already considered.

Search existing issues first — there's a good chance someone already raised it.

## Development setup

1. Install Android Studio Hedgehog or newer.
2. Install JDK 17 (already configured in `gradle.properties`).
3. Install Android SDK 34, build-tools 34.0.0, and platform-tools.
4. Clone and open the project in Android Studio.
5. Build and install on a device or emulator: `./gradlew installDebug`.

The mushaf page WebPs and `ayahinfo.db` are committed to the repo, so a fresh clone builds without any extra download step.

## Branching and commits

- Branch off `main` for every change. Use a short prefix: `feature/`, `fix/`, `refactor/`, `docs/`.
- Keep commits focused. One logical change per commit, present-tense imperative subject (e.g. `Fix bookmark toggle scale animation`).
- Reference the GitHub issue in the subject when possible: `Fix #42 …`.
- Never commit secrets, signing keys, or `local.properties`.

## Coding conventions

### Kotlin

- Follow Kotlin's official style guide; let Android Studio reformat with the bundled IntelliJ profile.
- Prefer immutable `val` over `var`, data classes for value types, sealed classes / interfaces for closed hierarchies.
- Suspend functions for async work, `Flow` for streams; avoid blocking the main thread.

### Compose

- One composable per file when it's a screen; multiple per file is fine for small private helpers.
- Hoist state — composables take `state` and `onAction` parameters, viewmodels live one level above.
- Use `MaterialTheme.colorScheme` and `MaterialTheme.typography`; never hard-code colours or text sizes outside `theme/`.
- Animation specs live in `ui/theme/Motion.kt` — reuse `Motion.short()` / `Motion.standard()` / `Motion.emphasized()` instead of rolling new tween durations.
- Keep `Modifier` chains in this order: layout → drawing → input → semantics.

### Architecture

- ViewModels stay UI-agnostic; expose `StateFlow`, accept events via methods.
- Repositories own data sources (Room DAO, Retrofit API, DataStore) and return domain models.
- Hilt modules go in `di/`; one module per concern (`AppModule`, `AyahInfoModule`).
- Database changes need a Room `Migration` — no destructive migrations on user-facing data tables.

## Testing

- Unit tests: `./gradlew test` (JVM, JUnit 4).
- Instrumented tests: `./gradlew connectedAndroidTest` (requires a connected device or emulator).
- Lint: `./gradlew :app:lint`.

A change that touches business logic should ship with at least one unit test. UI tweaks don't require tests but should include a screenshot or screen recording in the PR description.

## Pull requests

1. Push your branch and open a PR against `main`.
2. Fill in the PR template: what changed, why, screenshots, test plan.
3. Confirm `./gradlew assembleDebug` and `./gradlew :app:lint` are green.
4. Address review feedback in additional commits — squashing happens when the PR is merged.

Maintainers aim to review within a few days. Ping the issue if a PR has been quiet for over a week.

## Translations

The app currently ships English and Bahasa Indonesia. To add a new language:

1. Create `app/src/main/res/values-<locale>/strings.xml` (use the BCP-47 code Android expects, e.g. `values-ar` for Arabic).
2. Translate every `<string name="…">` from `values/strings.xml`. Keep `%1$s` / `%1$d` placeholders intact and in the same order.
3. Add the locale to `data/preferences/Language.kt` and the language picker in `SettingsScreen.kt`.
4. Verify the layout in `LayoutDirection.Rtl` if your locale is RTL.

## License

By contributing, you agree that your contributions are licensed under the MIT License — see [LICENSE](LICENSE).
