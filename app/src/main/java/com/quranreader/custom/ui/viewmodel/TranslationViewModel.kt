package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.model.TranslationEdition
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.data.preferences.TranslationScope
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the in-reader translation panel.
 *
 *  - The reader pushes the current mushaf page into [setCurrentPage]
 *    and the highlighted ayah into [setHighlightedAyah]. Everything
 *    else (which edition, which scope) is read from [UserPreferences].
 *  - [translations] is the list of translated verses for the active
 *    page in the user's chosen edition. The panel renders this list;
 *    when scope = HIGHLIGHTED_ONLY it filters down to the single
 *    highlighted ayah on render so we don't duplicate the slicing
 *    logic in the View.
 *  - The catalogue picker uses [editions] (the entire quran.com
 *    catalogue mirrored locally) and the per-edition download / delete
 *    actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // ── Reader-side state ────────────────────────────────────────────────────

    private val _currentPage = MutableStateFlow(1)

    /**
     * `(surahNumber, ayahNumber)` of the currently highlighted verse,
     * or `null` when the user has dismissed the highlight. Used to
     * (a) drive the highlighted-only filter, and (b) accent the
     * matching row when scope = ENTIRE_PAGE.
     */
    private val _highlighted = MutableStateFlow<Pair<Int, Int>?>(null)

    val highlightedAyahNumber: StateFlow<Int?> = MutableStateFlow<Int?>(null).also { sink ->
        viewModelScope.launch {
            _highlighted.collectLatest { sink.value = it?.second }
        }
    }.asStateFlow()

    /** Edition the user is currently reading. Persists across launches. */
    val translationEditionId: StateFlow<Int> = userPreferences.translationEditionId.stateIn(
        viewModelScope, SharingStarted.Eagerly, 131
    )

    /** Three-letter language code (legacy, kept for daily-verse worker). */
    val translationLanguage: StateFlow<String> = userPreferences.translationLanguage.stateIn(
        viewModelScope, SharingStarted.Eagerly, "en"
    )

    val translationScope: StateFlow<TranslationScope> = userPreferences.translationScope.stateIn(
        viewModelScope, SharingStarted.Eagerly, TranslationScope.ENTIRE_PAGE
    )

    val showTranslationPanel: StateFlow<Boolean> = userPreferences.isTranslationPanelOpen.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    // ── Catalogue / picker ───────────────────────────────────────────────────

    val editions: StateFlow<List<TranslationEdition>> = translationRepository.observeEditions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The catalogue row matching [translationEditionId], observed
     * reactively. Re-emits whenever the picker swaps editions OR the
     * underlying row changes (i.e. a download completes /
     * `verseCount` flips, or the user deletes the edition). Used as
     * the trigger for [translations] and [isCurrentEditionInstalled]
     * so the in-reader panel updates the moment the download finishes
     * — no need for the user to flip pages or reopen the panel.
     */
    private val currentEditionRow: StateFlow<TranslationEdition?> = combine(
        translationEditionId,
        editions,
    ) { id, list -> list.firstOrNull { it.editionId == id } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Translations for the current page in the current edition.
     * Recomputes whenever the page changes, the user picks a different
     * edition, **or** the chosen edition's row updates (download
     * completes / delete). A coroutine inside [flow] runs the
     * suspending repository call so we never re-query unnecessarily.
     */
    val translations: StateFlow<List<TranslationText>> = combine(
        _currentPage,
        currentEditionRow,
    ) { page, row -> page to (row?.editionId ?: 0) }
        .distinctUntilChanged()
        .flatMapLatest { (page, edition) ->
            flow { emit(translationRepository.getTranslationsForPage(page, edition)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Whether the chosen edition has any rows in the local DB. Derived
     * from [currentEditionRow] (which already observes the catalogue
     * table) so it flips to `true` automatically when the user
     * downloads the edition for the first time.
     */
    val isCurrentEditionInstalled: StateFlow<Boolean> = currentEditionRow
        .map { it?.isDownloaded == true && it.verseCount > 0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val installedEditions: StateFlow<List<TranslationEdition>> = translationRepository.observeInstalledEditions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-edition transient state: 0..6236 = downloading, null = idle. */
    private val _downloadProgress = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _catalogueRefreshing = MutableStateFlow(false)
    val catalogueRefreshing: StateFlow<Boolean> = _catalogueRefreshing.asStateFlow()

    // ── Daily-verse / reminder prefs (kept here for SettingsScreen) ──────────

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

    // ── Inputs from UI ───────────────────────────────────────────────────────

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    /**
     * Push the (surah, ayah) the user just tapped on the mushaf so
     * the panel can either filter to that verse or accent it inside
     * the page list. Pass `null` to clear.
     */
    fun setHighlightedAyah(surah: Int?, ayah: Int?) {
        _highlighted.value = if (surah != null && ayah != null && surah > 0 && ayah > 0) {
            surah to ayah
        } else {
            null
        }
    }

    fun openPanel() {
        viewModelScope.launch { userPreferences.setTranslationPanelOpen(true) }
    }

    fun closePanel() {
        viewModelScope.launch { userPreferences.setTranslationPanelOpen(false) }
    }

    fun togglePanel() {
        viewModelScope.launch {
            userPreferences.setTranslationPanelOpen(!showTranslationPanel.value)
        }
    }

    fun setEdition(editionId: Int) {
        viewModelScope.launch { userPreferences.setTranslationEditionId(editionId) }
    }

    fun setScope(scope: TranslationScope) {
        viewModelScope.launch { userPreferences.setTranslationScope(scope) }
    }

    fun toggleScope() {
        viewModelScope.launch {
            val next = if (translationScope.value == TranslationScope.HIGHLIGHTED_ONLY) {
                TranslationScope.ENTIRE_PAGE
            } else {
                TranslationScope.HIGHLIGHTED_ONLY
            }
            userPreferences.setTranslationScope(next)
        }
    }

    /** Pull the latest editions catalogue from quran.com. */
    fun refreshCatalogue() {
        if (_catalogueRefreshing.value) return
        _catalogueRefreshing.value = true
        viewModelScope.launch {
            translationRepository.refreshEditionCatalogue()
            _catalogueRefreshing.value = false
        }
    }

    fun downloadEdition(edition: TranslationEdition) {
        if (_downloadProgress.value.containsKey(edition.editionId)) return
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (edition.editionId to DownloadProgress(0, 6236))
            try {
                val lang = edition.languageName ?: "en"
                translationRepository.downloadEdition(edition.editionId, lang) { downloaded, total ->
                    _downloadProgress.value = _downloadProgress.value +
                        (edition.editionId to DownloadProgress(downloaded, total))
                }
            } finally {
                _downloadProgress.value = _downloadProgress.value - edition.editionId
            }
        }
    }

    fun deleteEdition(editionId: Int) {
        viewModelScope.launch { translationRepository.deleteEdition(editionId) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { userPreferences.setTranslationLanguage(lang) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setReminderEnabled(enabled) }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch { userPreferences.setReminderTime(hour, minute) }
    }

    fun setDailyVerseEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDailyVerseEnabled(enabled) }
    }

    data class DownloadProgress(val downloaded: Int, val total: Int) {
        val fraction: Float get() = if (total <= 0) 0f else (downloaded.toFloat() / total).coerceIn(0f, 1f)
    }
}
