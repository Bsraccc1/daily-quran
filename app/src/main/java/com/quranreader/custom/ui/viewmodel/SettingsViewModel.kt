package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.memorization.MemorizationRepository
import com.quranreader.custom.data.preferences.AutoSaveMode
import com.quranreader.custom.data.preferences.DisplayMode
import com.quranreader.custom.data.preferences.ReadingMode
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val bookmarkRepository: BookmarkRepository,
    private val memorizationRepository: MemorizationRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // F-09: Display Mode
    val displayMode = userPreferences.displayMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DisplayMode.SYSTEM
    )

    // Language
    val appLanguage = userPreferences.appLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "en"
    )

    // First launch status
    val isFirstLaunchComplete = userPreferences.isFirstLaunchComplete.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    // ── Theme System ─────────────────────────────────────────────────────────
    val themeId = userPreferences.themeId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "zamrud_light"
    )

    fun setThemeId(id: String) {
        viewModelScope.launch {
            userPreferences.setThemeId(id)
        }
    }

    // ── Reading Mode (v10) ───────────────────────────────────────────────────
    /**
     * Reader presentation style. Bound to the Reading Style chip
     * pair in the Reading section of Settings. Default
     * [ReadingMode.MUSHAF] mirrors the bundled-WebP reader every
     * pre-v10 user already knows.
     */
    val readingMode = userPreferences.readingMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadingMode.MUSHAF
    )

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            userPreferences.setReadingMode(mode)
        }
    }

    // ── Session Limits ───────────────────────────────────────────────────────
    val newSessionLimit = userPreferences.newSessionLimit.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 5
    )

    fun setNewSessionLimit(limit: Int) {
        viewModelScope.launch {
            userPreferences.setNewSessionLimit(limit)
        }
    }

    val continueReadingLimit = userPreferences.continueReadingLimit.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 2
    )

    fun setContinueReadingLimit(limit: Int) {
        viewModelScope.launch {
            userPreferences.setContinueReadingLimit(limit)
        }
    }

    /**
     * Auto-save dwell window in seconds (1..60). The reader's
     * slide-down panel renders a countdown chip backed by this value
     * and persists session progress once the timer elapses, so users
     * can balance "save aggressively" against "minimize datastore
     * writes" without us guessing.
     */
    val autoSavePageSeconds = userPreferences.autoSavePageSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 3
    )

    fun setAutoSavePageSeconds(seconds: Int) {
        viewModelScope.launch {
            userPreferences.setAutoSavePageSeconds(seconds)
        }
    }

    /**
     * Active auto-save trigger ([AutoSaveMode.BY_TIME] vs
     * [AutoSaveMode.BY_PAGES]). Drives both the reader's persistence
     * collector and the auto-save chip's label format. Defaults to
     * BY_TIME for legacy parity with users who never open the picker.
     */
    val autoSaveMode = userPreferences.autoSaveMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutoSaveMode.BY_TIME,
    )

    fun setAutoSaveMode(mode: AutoSaveMode) {
        viewModelScope.launch {
            userPreferences.setAutoSaveMode(mode)
        }
    }

    /**
     * Page-count threshold that arms [AutoSaveMode.BY_PAGES]. Range
     * 1..50 enforced on the setter so a misclick can't disable saves
     * entirely. The reader binds to this so changing the value mid-
     * session takes effect on the very next page flip — no restart.
     */
    val autoSavePageCount = userPreferences.autoSavePageCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 3,
    )

    fun setAutoSavePageCount(pages: Int) {
        viewModelScope.launch {
            userPreferences.setAutoSavePageCount(pages)
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch {
            userPreferences.setDisplayMode(mode)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            userPreferences.setAppLanguage(languageCode)
            // Also save to SharedPreferences for quick access on app start
            com.quranreader.custom.utils.LocaleManager.saveLanguagePreference(context, languageCode)
        }
    }
    
    fun shouldShowRestartDialog(newLanguageCode: String): Boolean {
        return appLanguage.value != newLanguageCode
    }

    // F-07: Clear all bookmarks
    fun clearAllBookmarks() {
        viewModelScope.launch {
            bookmarkRepository.clearAll()
        }
    }

    /**
     * Wipe **everything** the user could call "history": all
     * bookmarks, all multi-session entries, the active legacy
     * session, the last-page pointer, the lifetime reading stats,
     * and every memorization session row. Configuration (theme,
     * language, reciter, reminders) is intentionally untouched —
     * those aren't history, they're settings, and the user wouldn't
     * expect them to reset just because they cleared "history".
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            bookmarkRepository.clearAll()
            memorizationRepository.clearAll()
            userPreferences.clearAllHistory()
        }
    }
}
