package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.model.AvailableTranslation
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TranslationDisplayMode { HIGHLIGHTED_ONLY, ALL_ON_PAGE;

    fun storedValue(): String = when (this) {
        HIGHLIGHTED_ONLY -> "highlighted"
        ALL_ON_PAGE -> "all_on_page"
    }

    companion object {
        fun fromStored(s: String?): TranslationDisplayMode =
            if (s == "all_on_page") ALL_ON_PAGE else HIGHLIGHTED_ONLY
    }
}

@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** Translations cached for the page currently being viewed. */
    private val _pageTranslations = MutableStateFlow<List<TranslationText>>(emptyList())
    val pageTranslations: StateFlow<List<TranslationText>> = _pageTranslations.asStateFlow()

    /** Backwards-compat alias for the old field name still referenced in some screens. */
    val translations: StateFlow<List<TranslationText>> = pageTranslations

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Active translation IDs the side panel should render. */
    private val _activeTranslationIds = MutableStateFlow<List<Int>>(emptyList())
    val activeTranslationIds: StateFlow<List<Int>> = _activeTranslationIds.asStateFlow()

    /** True if at least one of the active translation IDs is downloaded. */
    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    private val _showTranslationPanel = MutableStateFlow(false)
    val showTranslationPanel: StateFlow<Boolean> = _showTranslationPanel.asStateFlow()

    /** Backwards-compat alias kept for older call sites. */
    val showTranslationSheet: StateFlow<Boolean> = showTranslationPanel

    private val _highlightedAyahNumber = MutableStateFlow<Int?>(null)
    val highlightedAyahNumber: StateFlow<Int?> = _highlightedAyahNumber.asStateFlow()

    private val _displayMode = MutableStateFlow(TranslationDisplayMode.HIGHLIGHTED_ONLY)
    val displayMode: StateFlow<TranslationDisplayMode> = _displayMode.asStateFlow()

    /** Catalog of editions advertised by quran.com (lazy-loaded). */
    private val _availableTranslations = MutableStateFlow<List<AvailableTranslation>>(emptyList())
    val availableTranslations: StateFlow<List<AvailableTranslation>> = _availableTranslations.asStateFlow()

    /** Currently downloaded edition IDs. Drives manager UI state. */
    private val _downloadedTranslationIds = MutableStateFlow<List<Int>>(emptyList())
    val downloadedTranslationIds: StateFlow<List<Int>> = _downloadedTranslationIds.asStateFlow()

    /** Per-edition download progress (0-100). Keyed by translationId. */
    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()

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

    private var lastLoadedPage: Int = -1

    init {
        // Refresh active edition IDs / display mode / downloaded list from prefs.
        viewModelScope.launch {
            userPreferences.activeTranslationIds.collect { ids ->
                val resolved = if (ids.isEmpty()) defaultIdForLanguage(translationLanguage.value) else ids
                _activeTranslationIds.value = resolved
                refreshIsDownloaded()
                if (lastLoadedPage > 0) loadTranslationsForPage(lastLoadedPage)
            }
        }
        viewModelScope.launch {
            userPreferences.translationDisplayMode.collect { stored ->
                _displayMode.value = TranslationDisplayMode.fromStored(stored)
            }
        }
        viewModelScope.launch {
            translationRepository.observeDownloadedTranslationIds().collect {
                _downloadedTranslationIds.value = it
                refreshIsDownloaded()
            }
        }
    }

    /** Resolve the bundled default edition for the current language. */
    private fun defaultIdForLanguage(lang: String): List<Int> = when (lang) {
        "id" -> listOf(33)
        else -> listOf(131)
    }

    private fun refreshIsDownloaded() {
        val active = _activeTranslationIds.value
        val downloaded = _downloadedTranslationIds.value.toSet()
        _isDownloaded.value = active.any { it in downloaded }
    }

    fun loadTranslationsForPage(page: Int) {
        lastLoadedPage = page
        viewModelScope.launch {
            val ids = _activeTranslationIds.value
            if (ids.isEmpty() || !_isDownloaded.value) {
                _pageTranslations.value = emptyList()
                return@launch
            }
            _isLoading.value = true
            _pageTranslations.value = translationRepository.getTranslationsForPageMulti(page, ids)
            _isLoading.value = false
        }
    }

    fun toggleTranslationPanel() {
        _showTranslationPanel.value = !_showTranslationPanel.value
    }

    fun toggleTranslationSheet() = toggleTranslationPanel()

    fun closePanel() {
        _showTranslationPanel.value = false
    }

    fun setAyahHighlight(ayahNumber: Int?) {
        _highlightedAyahNumber.value = ayahNumber
        if (ayahNumber != null && !_showTranslationPanel.value) {
            _showTranslationPanel.value = true
        }
    }

    fun setDisplayMode(mode: TranslationDisplayMode) {
        _displayMode.value = mode
        viewModelScope.launch { userPreferences.setTranslationDisplayMode(mode.storedValue()) }
    }

    fun setActiveTranslationIds(ids: List<Int>) {
        viewModelScope.launch { userPreferences.setActiveTranslationIds(ids) }
    }

    fun setTranslationLanguage(lang: String) {
        viewModelScope.launch { userPreferences.setTranslationLanguage(lang) }
    }

    /** Load the catalog from quran.com (or the bundled fallback). */
    fun refreshAvailableTranslations() {
        viewModelScope.launch {
            _availableTranslations.value = translationRepository.fetchAvailableTranslations()
        }
    }

    fun downloadTranslationEdition(edition: AvailableTranslation) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (edition.id to 0f)
            val ok = translationRepository.downloadTranslationById(edition) { processed, total ->
                val pct = if (total > 0) processed.toFloat() / total else 0f
                _downloadProgress.value = _downloadProgress.value + (edition.id to pct)
            }
            // Drop the progress entry once the download finishes — UI
            // will rely on `downloadedTranslationIds` from then on.
            _downloadProgress.value = _downloadProgress.value - edition.id
            if (ok) {
                // If no active edition was set yet, auto-activate the
                // first download so the panel just works.
                if (_activeTranslationIds.value.none { it in _downloadedTranslationIds.value }) {
                    setActiveTranslationIds(listOf(edition.id))
                }
            }
        }
    }

    fun deleteTranslationEdition(translationId: Int) {
        viewModelScope.launch {
            translationRepository.deleteTranslation(translationId)
            // Remove from active selection if it was active.
            val current = _activeTranslationIds.value
            if (translationId in current) {
                setActiveTranslationIds(current - translationId)
            }
        }
    }

    // ── Legacy daily-verse / reminder hooks ──────────────────────────

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setReminderEnabled(enabled) }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch { userPreferences.setReminderTime(hour, minute) }
    }

    fun setDailyVerseEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDailyVerseEnabled(enabled) }
    }
}
