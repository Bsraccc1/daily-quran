package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _translations = MutableStateFlow<List<TranslationText>>(emptyList())
    val translations: StateFlow<List<TranslationText>> = _translations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    private val _showTranslationSheet = MutableStateFlow(false)
    val showTranslationSheet: StateFlow<Boolean> = _showTranslationSheet.asStateFlow()

    private val _highlightedAyahNumber = MutableStateFlow<Int?>(null)
    val highlightedAyahNumber: StateFlow<Int?> = _highlightedAyahNumber.asStateFlow()

    val translationLanguage: StateFlow<String> = userPreferences.translationLanguage.stateIn(
        viewModelScope, SharingStarted.Eagerly, "en"
    )

    val reminderEnabled: StateFlow<Boolean> = userPreferences.reminderEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val dailyVerseEnabled: StateFlow<Boolean> = userPreferences.dailyVerseEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val reminderHour: StateFlow<Int> = userPreferences.reminderHour.stateIn(
        viewModelScope, SharingStarted.Eagerly, 8
    )

    val reminderMinute: StateFlow<Int> = userPreferences.reminderMinute.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    init {
        viewModelScope.launch {
            translationLanguage.collect { lang ->
                _isDownloaded.value = translationRepository.isLanguageDownloaded(lang)
            }
        }
    }

    fun loadTranslationsForPage(page: Int) {
        viewModelScope.launch {
            val lang = translationLanguage.value
            if (!_isDownloaded.value) return@launch

            _isLoading.value = true
            val result = translationRepository.getTranslationsForPage(page, lang)
            _translations.value = result
            _isLoading.value = false
        }
    }

    fun toggleTranslationSheet() {
        _showTranslationSheet.value = !_showTranslationSheet.value
    }

    fun setAyahHighlight(ayahNumber: Int?) {
        _highlightedAyahNumber.value = ayahNumber
        if (ayahNumber != null && !_showTranslationSheet.value) {
            _showTranslationSheet.value = true
        }
    }

    fun setTranslationLanguage(lang: String) {
        viewModelScope.launch {
            userPreferences.setTranslationLanguage(lang)
        }
    }

    fun downloadTranslation(onProgress: (Int, Int) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = translationRepository.downloadTranslation(
                translationLanguage.value, onProgress
            )
            if (success) {
                _isDownloaded.value = true
            }
            _isLoading.value = false
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setReminderEnabled(enabled)
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferences.setReminderTime(hour, minute)
        }
    }

    fun setDailyVerseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDailyVerseEnabled(enabled)
        }
    }
}
