package com.quranreader.custom.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class DisplayMode { LIGHT, DARK, SYSTEM }

/**
 * Whether the translation panel renders a single verse (the
 * highlighted one only) or the entire page's worth of verses with
 * the highlighted ayah accented inside the list. Mirrors the
 * "Translation scope" toggle in the swipe-up reader panel.
 */
enum class TranslationScope { HIGHLIGHTED_ONLY, ENTIRE_PAGE }

/**
 * Strategy the reader uses to decide *when* to commit session
 * progress to DataStore.
 *
 * - [BY_TIME] (default, legacy): debounce on the user dwelling on
 *   the same page for [UserPreferences.autoSavePageSeconds] seconds.
 *   Best for users who pause to reflect on every ayah — quick
 *   swipes don't pollute their progress, real reads do.
 * - [BY_PAGES]: commit every Nth page flip
 *   ([UserPreferences.autoSavePageCount]) regardless of dwell time.
 *   Best for users who batch-read many pages quickly and don't want
 *   the dwell timer holding their progress hostage.
 *
 * The reader's auto-save chip mirrors whichever mode is active:
 * BY_TIME shows a seconds countdown ring, BY_PAGES shows a
 * "X / N pages" progress ring.
 */
enum class AutoSaveMode { BY_TIME, BY_PAGES }

/**
 * Reader orientation override. AUTO follows the device sensor
 * (which itself respects the user's system rotation lock); the
 * other two pin the reader regardless of how the phone is held.
 * Surfaced as a toggle in the swipe-up panel so users can lock to
 * landscape for the zoomed-in view without flipping their device's
 * global rotation lock.
 */
enum class ReaderOrientation { AUTO, PORTRAIT, LANDSCAPE }

/**
 * A named reading session. Multiple sessions can coexist.
 * Stored as a flat list in DataStore (serialized manually — no Gson dependency needed).
 */
data class ReadingSession(
    val id: String,
    val name: String,
    val startPage: Int,
    val targetPages: Int,
    val pagesRead: Int = 0,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false
)

@Singleton
class UserPreferences @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val DISPLAY_MODE_KEY          = stringPreferencesKey("display_mode")
        private val LAST_PAGE_KEY             = intPreferencesKey("last_page")
        // Legacy single-session keys (kept for ReadingViewModel backward compat)
        private val SESSION_ACTIVE_KEY        = booleanPreferencesKey("session_active")
        private val SESSION_START_PAGE_KEY    = intPreferencesKey("session_start_page")
        private val SESSION_TARGET_PAGES_KEY  = intPreferencesKey("session_target_pages")
        private val AUDIO_REPEAT_COUNT_KEY    = intPreferencesKey("audio_repeat_count")
        private val APP_LANGUAGE_KEY          = stringPreferencesKey("app_language")
        private val TOTAL_PAGES_READ_KEY      = intPreferencesKey("total_pages_read")
        private val READING_TIME_MINUTES_KEY  = intPreferencesKey("reading_time_minutes")
        private val FIRST_LAUNCH_COMPLETE_KEY = booleanPreferencesKey("first_launch_complete")
        private val DOWNLOAD_PROGRESS_KEY     = intPreferencesKey("download_progress")
        private val DOWNLOAD_TOTAL_KEY        = intPreferencesKey("download_total")
        // Multi-session: "id|name|startPage|targetPages|pagesRead|isActive|isCompleted" joined by ";"
        private val SESSIONS_KEY              = stringPreferencesKey("reading_sessions")
        private val ACTIVE_SESSION_ID_KEY     = stringPreferencesKey("active_session_id")

        // ── Theme System ─────────────────────────────────────────────────────
        private val THEME_ID_KEY              = stringPreferencesKey("theme_id")

        // ── Session Limits ───────────────────────────────────────────────────
        private val NEW_SESSION_LIMIT         = intPreferencesKey("new_session_limit")
        private val CONTINUE_READING_LIMIT    = intPreferencesKey("continue_reading_limit")
        // Auto-save dwell window for the reader's progress indicator.
        // The reader exposes a small countdown chip in its top panel
        // and persists session progress once the timer elapses; users
        // can shorten it (peace of mind) or lengthen it (battery /
        // datastore churn) from Settings → Reading.
        private val AUTO_SAVE_PAGE_SECONDS    = intPreferencesKey("auto_save_page_seconds")
        // Which auto-save trigger the reader uses — dwell timer or
        // page-count threshold. Stored as the [AutoSaveMode] enum
        // name; missing / unknown values fall back to BY_TIME so
        // pre-v10 users keep the legacy behavior on first launch.
        private val AUTO_SAVE_MODE_KEY        = stringPreferencesKey("auto_save_mode")
        // Page-count threshold for [AutoSaveMode.BY_PAGES]. Range
        // 1..50 — a one-page threshold is essentially "save every
        // flip", a 50-page threshold means batched saves only after
        // a full juz-ish stretch.
        private val AUTO_SAVE_PAGE_COUNT_KEY  = intPreferencesKey("auto_save_page_count")

        // ── Translation ────────────────────────────────────────────────────
        private val TRANSLATION_LANGUAGE_KEY  = stringPreferencesKey("translation_language")
        // v9: edition picked from the quran.com catalogue. Defaults to
        // 131 (Saheeh International) which maps from the legacy 'en'
        // download via MIGRATION_8_9.
        private val TRANSLATION_EDITION_KEY   = intPreferencesKey("translation_edition_id")
        // Translation scope: "highlighted" or "page" (matches
        // [TranslationScope] enum values, lowercase). Defaults to
        // "page" so the panel feels like the previous behaviour
        // until the user picks otherwise.
        private val TRANSLATION_SCOPE_KEY     = stringPreferencesKey("translation_scope")
        // Whether the translation panel is currently visible. The
        // reader binds to this so swiping up shows / hides the panel
        // and the choice survives configuration changes.
        private val TRANSLATION_PANEL_OPEN    = booleanPreferencesKey("translation_panel_open")
        // Reader orientation override. Stored as the enum name.
        private val READER_ORIENTATION_KEY    = stringPreferencesKey("reader_orientation")
        // Selected quran.com recitation_id. Falls back to 0 meaning
        // "use the legacy ReciterConfig list" so existing users keep
        // hearing whoever they had picked before the picker rewrite.
        private val SELECTED_RECITATION_ID_KEY = intPreferencesKey("selected_recitation_id")

        // ── Reminders & Daily Verse ────────────────────────────────────────
        private val REMINDER_ENABLED_KEY      = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_HOUR_KEY         = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE_KEY       = intPreferencesKey("reminder_minute")
        private val DAILY_VERSE_ENABLED_KEY   = booleanPreferencesKey("daily_verse_enabled")

        // ── Audio ──────────────────────────────────────────────────────────
        private val SELECTED_RECITER_KEY      = stringPreferencesKey("selected_reciter")

        // ── v3.0: Onboarding ──────────────────────────────────────────────
        private val V3_ONBOARDING_SHOWN_KEY   = booleanPreferencesKey("v3_onboarding_shown")

        // ── Reading Mode (v10) ─────────────────────────────────────────────
        // Picks between Mushaf (page-by-page WebP) and Translation
        // (per-juz scrollable verse list). Persisted as the enum name.
        private val READING_MODE_KEY          = stringPreferencesKey("reading_mode")
        // Last-read (surah, ayah) — single source of truth across both
        // readers. The legacy LAST_PAGE_KEY is still written by the
        // mushaf reader so the Continue Reading CTA keeps working.
        private val LAST_SURAH_KEY            = intPreferencesKey("last_surah")
        private val LAST_AYAH_KEY             = intPreferencesKey("last_ayah")
    }

    // ── v3.0 onboarding flag ──────────────────────────────────────────────
    val v3OnboardingShown: Flow<Boolean> = dataStore.data.map { it[V3_ONBOARDING_SHOWN_KEY] ?: false }
    suspend fun setV3OnboardingShown(shown: Boolean) = dataStore.edit { it[V3_ONBOARDING_SHOWN_KEY] = shown }

    // ── Theme ─────────────────────────────────────────────────────────────────
    val themeId: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_ID_KEY] ?: "zamrud_light"
    }
    suspend fun setThemeId(id: String) = dataStore.edit { it[THEME_ID_KEY] = id }

    // ── Reading Mode (v10) ────────────────────────────────────────────────────
    /**
     * Reader presentation style — Mushaf page renderer or per-juz
     * Translation list. Defaults to [ReadingMode.MUSHAF] so every
     * pre-v10 user sees the existing reader on first launch.
     */
    val readingMode: Flow<ReadingMode> = dataStore.data.map { prefs ->
        ReadingMode.fromName(prefs[READING_MODE_KEY])
    }
    suspend fun setReadingMode(mode: ReadingMode) = dataStore.edit {
        it[READING_MODE_KEY] = mode.name
    }

    /**
     * Last-read position as `(surah, ayah)`. Defaults to (1, 1) — Al-Fatihah
     * verse 1 — for first launches. Both readers maintain this on
     * scroll/page-flip so "Continue Reading" always lands the user where
     * they left off, regardless of which reader saved it.
     */
    val lastSurah: Flow<Int> = dataStore.data.map { it[LAST_SURAH_KEY] ?: 1 }
    val lastAyah:  Flow<Int> = dataStore.data.map { it[LAST_AYAH_KEY] ?: 1 }
    suspend fun setLastSurahAyah(surah: Int, ayah: Int) = dataStore.edit {
        it[LAST_SURAH_KEY] = surah
        it[LAST_AYAH_KEY]  = ayah
    }

    // ── Session Limits ────────────────────────────────────────────────────────
    val newSessionLimit: Flow<Int> = dataStore.data.map { prefs ->
        prefs[NEW_SESSION_LIMIT] ?: 5
    }
    suspend fun setNewSessionLimit(limit: Int) = dataStore.edit {
        it[NEW_SESSION_LIMIT] = limit.coerceIn(1, 50)
    }

    val continueReadingLimit: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CONTINUE_READING_LIMIT] ?: 2
    }
    suspend fun setContinueReadingLimit(limit: Int) = dataStore.edit {
        it[CONTINUE_READING_LIMIT] = limit.coerceIn(1, 50)
    }

    /**
     * How long (in seconds) the reader waits on a stable page before
     * persisting the session progress. The same value drives the
     * auto-save countdown chip in the slide-down panel so the user
     * can see exactly when their progress will be committed.
     *
     * Defaults to 3 seconds — long enough to weed out swipes the user
     * is just glancing through, short enough that backing out of the
     * reader after a real read still saves before the activity is
     * destroyed. Range 1..60 is enforced on the setter so a misclick
     * in the Settings field can't accidentally disable saves.
     */
    val autoSavePageSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[AUTO_SAVE_PAGE_SECONDS] ?: 3
    }
    suspend fun setAutoSavePageSeconds(seconds: Int) = dataStore.edit {
        it[AUTO_SAVE_PAGE_SECONDS] = seconds.coerceIn(1, 60)
    }

    /**
     * Which trigger the reader uses to decide when to persist
     * session progress. See [AutoSaveMode]. Defaults to
     * [AutoSaveMode.BY_TIME] so users who never open the picker
     * keep the legacy dwell-timer behaviour. Unknown / corrupted
     * values also fall back to BY_TIME.
     */
    val autoSaveMode: Flow<AutoSaveMode> = dataStore.data.map { prefs ->
        when (prefs[AUTO_SAVE_MODE_KEY]) {
            AutoSaveMode.BY_PAGES.name -> AutoSaveMode.BY_PAGES
            else -> AutoSaveMode.BY_TIME
        }
    }
    suspend fun setAutoSaveMode(mode: AutoSaveMode) = dataStore.edit {
        it[AUTO_SAVE_MODE_KEY] = mode.name
    }

    /**
     * How many pages the user must flip past before
     * [AutoSaveMode.BY_PAGES] commits session progress to
     * DataStore. Defaults to 3 — same numeric default as
     * [autoSavePageSeconds] so the picker feels symmetric on first
     * encounter — and is range-coerced to 1..50 on the setter so a
     * misclick can't disable saves entirely.
     */
    val autoSavePageCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[AUTO_SAVE_PAGE_COUNT_KEY] ?: 3
    }
    suspend fun setAutoSavePageCount(pages: Int) = dataStore.edit {
        it[AUTO_SAVE_PAGE_COUNT_KEY] = pages.coerceIn(1, 50)
    }

    // ── Display Mode ──────────────────────────────────────────────────────────
    val displayMode: Flow<DisplayMode> = dataStore.data.map { prefs ->
        when (prefs[DISPLAY_MODE_KEY]) {
            "LIGHT" -> DisplayMode.LIGHT
            "DARK"  -> DisplayMode.DARK
            else    -> DisplayMode.SYSTEM
        }
    }
    suspend fun setDisplayMode(mode: DisplayMode) = dataStore.edit { it[DISPLAY_MODE_KEY] = mode.name }

    // ── Last Page ─────────────────────────────────────────────────────────────
    val lastPage: Flow<Int> = dataStore.data.map { it[LAST_PAGE_KEY] ?: 1 }
    suspend fun setLastPage(page: Int) = dataStore.edit { it[LAST_PAGE_KEY] = page }

    // ── Legacy single-session (used by ReadingViewModel for limit check) ──────
    val isSessionActive: Flow<Boolean> = dataStore.data.map { it[SESSION_ACTIVE_KEY] ?: false }
    val sessionStartPage: Flow<Int>    = dataStore.data.map { it[SESSION_START_PAGE_KEY] ?: 1 }
    val sessionTargetPages: Flow<Int>  = dataStore.data.map { it[SESSION_TARGET_PAGES_KEY] ?: 0 }

    suspend fun startSession(startPage: Int, targetPages: Int) = dataStore.edit {
        it[SESSION_ACTIVE_KEY]       = true
        it[SESSION_START_PAGE_KEY]   = startPage
        it[SESSION_TARGET_PAGES_KEY] = targetPages
    }
    suspend fun extendSession(additionalPages: Int) = dataStore.edit {
        it[SESSION_TARGET_PAGES_KEY] = (it[SESSION_TARGET_PAGES_KEY] ?: 0) + additionalPages
    }
    suspend fun endSession() = dataStore.edit { it[SESSION_ACTIVE_KEY] = false }

    // ── Multi-Session ─────────────────────────────────────────────────────────
    val sessions: Flow<List<ReadingSession>> = dataStore.data.map { prefs ->
        parseSessions(prefs[SESSIONS_KEY] ?: "")
    }
    val activeSessionId: Flow<String?> = dataStore.data.map { it[ACTIVE_SESSION_ID_KEY] }

    suspend fun addSession(session: ReadingSession) = dataStore.edit { prefs ->
        val current = parseSessions(prefs[SESSIONS_KEY] ?: "").toMutableList()
        current.add(session)
        prefs[SESSIONS_KEY] = serializeSessions(current)
    }
    suspend fun updateSession(updated: ReadingSession) = dataStore.edit { prefs ->
        val current = parseSessions(prefs[SESSIONS_KEY] ?: "").toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx >= 0) current[idx] = updated
        prefs[SESSIONS_KEY] = serializeSessions(current)
    }
    
    suspend fun updateSessionProgress(pagesRead: Int) = dataStore.edit { prefs ->
        val activeId = prefs[ACTIVE_SESSION_ID_KEY] ?: return@edit
        val current = parseSessions(prefs[SESSIONS_KEY] ?: "").toMutableList()
        val idx = current.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(pagesRead = pagesRead)
            prefs[SESSIONS_KEY] = serializeSessions(current)
        }
    }
    
    suspend fun deleteSession(sessionId: String) = dataStore.edit { prefs ->
        val current = parseSessions(prefs[SESSIONS_KEY] ?: "").filter { it.id != sessionId }
        prefs[SESSIONS_KEY] = serializeSessions(current)
        if (prefs[ACTIVE_SESSION_ID_KEY] == sessionId) prefs.remove(ACTIVE_SESSION_ID_KEY)
    }

    /**
     * Bump the active session's [ReadingSession.targetPages] by
     * [additionalPages]. Mirrors [extendSession] (which only touches
     * the legacy single-session keys) so the in-reader "Continue
     * Reading" sheet keeps the multi-session card's progress bar
     * accurate — without this the card would still show "10 / 10 ✓"
     * after the user extended for two more pages.
     */
    suspend fun extendActiveSession(additionalPages: Int) = dataStore.edit { prefs ->
        val activeId = prefs[ACTIVE_SESSION_ID_KEY] ?: return@edit
        val current = parseSessions(prefs[SESSIONS_KEY] ?: "").toMutableList()
        val idx = current.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            val s = current[idx]
            current[idx] = s.copy(targetPages = (s.targetPages + additionalPages).coerceAtLeast(1))
            prefs[SESSIONS_KEY] = serializeSessions(current)
        }
    }
    suspend fun setActiveSession(sessionId: String?) = dataStore.edit { prefs ->
        if (sessionId == null) prefs.remove(ACTIVE_SESSION_ID_KEY)
        else prefs[ACTIVE_SESSION_ID_KEY] = sessionId
    }

    /**
     * Wipe everything we treat as "user history": multi-session list,
     * legacy single-session keys, last page, and the reading stats.
     * Called by Settings → Clear all history. Doesn't touch the
     * theme, language, reciter, or reminder preferences — those are
     * configuration, not history.
     */
    suspend fun clearAllHistory() = dataStore.edit { prefs ->
        // Multi-session
        prefs.remove(SESSIONS_KEY)
        prefs.remove(ACTIVE_SESSION_ID_KEY)
        // Legacy single-session
        prefs.remove(SESSION_ACTIVE_KEY)
        prefs.remove(SESSION_START_PAGE_KEY)
        prefs.remove(SESSION_TARGET_PAGES_KEY)
        // Last page resets back to page 1
        prefs.remove(LAST_PAGE_KEY)
        // Reading stats — counts as history
        prefs.remove(TOTAL_PAGES_READ_KEY)
        prefs.remove(READING_TIME_MINUTES_KEY)
    }

    private fun serializeSessions(list: List<ReadingSession>): String =
        list.joinToString(";") { s ->
            "${s.id}|${s.name}|${s.startPage}|${s.targetPages}|${s.pagesRead}|${s.isActive}|${s.isCompleted}"
        }
    private fun parseSessions(raw: String): List<ReadingSession> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val p = entry.split("|")
            if (p.size < 7) return@mapNotNull null
            try {
                ReadingSession(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toBoolean(), p[6].toBoolean())
            } catch (_: Exception) { null }
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────
    val audioRepeatCount: Flow<Int> = dataStore.data.map { it[AUDIO_REPEAT_COUNT_KEY] ?: 1 }
    suspend fun setAudioRepeatCount(count: Int) = dataStore.edit { it[AUDIO_REPEAT_COUNT_KEY] = count }

    // ── Language ──────────────────────────────────────────────────────────────
    val appLanguage: Flow<String> = dataStore.data.map { it[APP_LANGUAGE_KEY] ?: "en" }
    suspend fun setAppLanguage(code: String) = dataStore.edit { it[APP_LANGUAGE_KEY] = code }

    // ── Stats ─────────────────────────────────────────────────────────────────
    val totalPagesRead: Flow<Int> = dataStore.data.map { it[TOTAL_PAGES_READ_KEY] ?: 0 }
    suspend fun incrementPagesRead() = dataStore.edit { it[TOTAL_PAGES_READ_KEY] = (it[TOTAL_PAGES_READ_KEY] ?: 0) + 1 }
    val readingTimeMinutes: Flow<Int> = dataStore.data.map { it[READING_TIME_MINUTES_KEY] ?: 0 }
    suspend fun addReadingTime(minutes: Int) = dataStore.edit { it[READING_TIME_MINUTES_KEY] = (it[READING_TIME_MINUTES_KEY] ?: 0) + minutes }

    // ── First Launch ──────────────────────────────────────────────────────────
    val isFirstLaunchComplete: Flow<Boolean> = dataStore.data.map { it[FIRST_LAUNCH_COMPLETE_KEY] ?: false }
    suspend fun setFirstLaunchComplete() = dataStore.edit { it[FIRST_LAUNCH_COMPLETE_KEY] = true }

    // ── Download Progress ─────────────────────────────────────────────────────
    suspend fun saveDownloadProgress(downloaded: Int, total: Int) = dataStore.edit {
        it[DOWNLOAD_PROGRESS_KEY] = downloaded
        it[DOWNLOAD_TOTAL_KEY]    = total
    }
    suspend fun clearDownloadProgress() = dataStore.edit {
        it.remove(DOWNLOAD_PROGRESS_KEY)
        it.remove(DOWNLOAD_TOTAL_KEY)
    }
    val downloadProgress: Flow<Pair<Int, Int>> = dataStore.data.map { prefs ->
        Pair(prefs[DOWNLOAD_PROGRESS_KEY] ?: 0, prefs[DOWNLOAD_TOTAL_KEY] ?: 604)
    }

    // ── Translation Language ───────────────────────────────────────────────
    val translationLanguage: Flow<String> = dataStore.data.map { it[TRANSLATION_LANGUAGE_KEY] ?: "en" }
    suspend fun setTranslationLanguage(lang: String) = dataStore.edit { it[TRANSLATION_LANGUAGE_KEY] = lang }

    /**
     * Quran.com `translation_id` of the currently selected edition.
     * Defaults to 131 (Saheeh International) — the same edition the
     * legacy 'en' download mapped to in MIGRATION_8_9, so users
     * upgrading from v8 see continuity instead of an empty picker.
     */
    val translationEditionId: Flow<Int> = dataStore.data.map { it[TRANSLATION_EDITION_KEY] ?: 131 }
    suspend fun setTranslationEditionId(editionId: Int) = dataStore.edit {
        it[TRANSLATION_EDITION_KEY] = editionId
    }

    /**
     * "highlighted" → only the user-tapped ayah's translation shows;
     * "page" → every translated ayah on the current page shows with the
     * highlighted ayah accented. Defaults to "page".
     */
    val translationScope: Flow<TranslationScope> = dataStore.data.map { prefs ->
        when (prefs[TRANSLATION_SCOPE_KEY]) {
            "highlighted" -> TranslationScope.HIGHLIGHTED_ONLY
            else -> TranslationScope.ENTIRE_PAGE
        }
    }
    suspend fun setTranslationScope(scope: TranslationScope) = dataStore.edit {
        it[TRANSLATION_SCOPE_KEY] = if (scope == TranslationScope.HIGHLIGHTED_ONLY) "highlighted" else "page"
    }

    val isTranslationPanelOpen: Flow<Boolean> = dataStore.data.map { it[TRANSLATION_PANEL_OPEN] ?: false }
    suspend fun setTranslationPanelOpen(open: Boolean) = dataStore.edit {
        it[TRANSLATION_PANEL_OPEN] = open
    }

    val readerOrientation: Flow<ReaderOrientation> = dataStore.data.map { prefs ->
        when (prefs[READER_ORIENTATION_KEY]) {
            "PORTRAIT" -> ReaderOrientation.PORTRAIT
            "LANDSCAPE" -> ReaderOrientation.LANDSCAPE
            else -> ReaderOrientation.AUTO
        }
    }
    suspend fun setReaderOrientation(orientation: ReaderOrientation) = dataStore.edit {
        it[READER_ORIENTATION_KEY] = orientation.name
    }

    /**
     * Quran.com `recitation_id` of the currently selected reciter.
     * 0 sentinels "use the legacy hard-coded list" so users
     * upgrading from v8 still hear whoever they had previously
     * selected via [selectedReciter].
     */
    val selectedRecitationId: Flow<Int> = dataStore.data.map { it[SELECTED_RECITATION_ID_KEY] ?: 0 }
    suspend fun setSelectedRecitationId(id: Int) = dataStore.edit {
        it[SELECTED_RECITATION_ID_KEY] = id
    }

    // ── Reminders ──────────────────────────────────────────────────────────
    val reminderEnabled: Flow<Boolean> = dataStore.data.map { it[REMINDER_ENABLED_KEY] ?: false }
    suspend fun setReminderEnabled(enabled: Boolean) = dataStore.edit { it[REMINDER_ENABLED_KEY] = enabled }

    val reminderHour: Flow<Int> = dataStore.data.map { it[REMINDER_HOUR_KEY] ?: 8 }
    val reminderMinute: Flow<Int> = dataStore.data.map { it[REMINDER_MINUTE_KEY] ?: 0 }
    suspend fun setReminderTime(hour: Int, minute: Int) = dataStore.edit {
        it[REMINDER_HOUR_KEY] = hour
        it[REMINDER_MINUTE_KEY] = minute
    }

    // ── Daily Verse ────────────────────────────────────────────────────────
    val dailyVerseEnabled: Flow<Boolean> = dataStore.data.map { it[DAILY_VERSE_ENABLED_KEY] ?: false }
    suspend fun setDailyVerseEnabled(enabled: Boolean) = dataStore.edit { it[DAILY_VERSE_ENABLED_KEY] = enabled }

    // ── Selected Reciter ───────────────────────────────────────────────────
    val selectedReciter: Flow<String> = dataStore.data.map { it[SELECTED_RECITER_KEY] ?: "abdul_basit_murattal" }
    suspend fun setSelectedReciter(id: String) = dataStore.edit { it[SELECTED_RECITER_KEY] = id }
}
