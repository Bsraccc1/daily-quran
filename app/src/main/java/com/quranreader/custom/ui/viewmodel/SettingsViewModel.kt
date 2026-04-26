package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.preferences.DisplayMode
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
}
