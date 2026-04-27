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

        // ── Translation ────────────────────────────────────────────────────
        private val TRANSLATION_LANGUAGE_KEY  = stringPreferencesKey("translation_language")

        // ── Reminders & Daily Verse ────────────────────────────────────────
        private val REMINDER_ENABLED_KEY      = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_HOUR_KEY         = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE_KEY       = intPreferencesKey("reminder_minute")
        private val DAILY_VERSE_ENABLED_KEY   = booleanPreferencesKey("daily_verse_enabled")

        // ── Audio ──────────────────────────────────────────────────────────
        private val SELECTED_RECITER_KEY      = stringPreferencesKey("selected_reciter")

        // ── v3.0: Onboarding ──────────────────────────────────────────────
        private val V3_ONBOARDING_SHOWN_KEY   = booleanPreferencesKey("v3_onboarding_shown")
    }

    // ── v3.0 onboarding flag ──────────────────────────────────────────────
    val v3OnboardingShown: Flow<Boolean> = dataStore.data.map { it[V3_ONBOARDING_SHOWN_KEY] ?: false }
    suspend fun setV3OnboardingShown(shown: Boolean) = dataStore.edit { it[V3_ONBOARDING_SHOWN_KEY] = shown }

    // ── Theme ─────────────────────────────────────────────────────────────────
    val themeId: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_ID_KEY] ?: "zamrud_light"
    }
    suspend fun setThemeId(id: String) = dataStore.edit { it[THEME_ID_KEY] = id }

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
