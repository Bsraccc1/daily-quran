package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.model.ArabicVerse
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.ArabicVerseRepository
import com.quranreader.custom.data.repository.BookmarkRepository
import com.quranreader.custom.data.repository.ReaderModeRepository
import com.quranreader.custom.data.repository.TranslationRepository
import com.quranreader.custom.data.repository.TransliterationRepository
import com.quranreader.custom.domain.PositionMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the per-juz [TranslationReaderScreen].
 *
 * Responsibilities:
 *  - Loads the Arabic + transliteration + translation rows for a
 *    selected juz (1..30).
 *  - Tracks the currently visible juz in [currentJuz].
 *  - Persists the user's last `(surah, ayah)` anchor so flipping
 *    back to Mushaf mode lands on the right page.
 *  - Emits one-off [Intent]s (play, bookmark, share, jump-to-mushaf)
 *    over a SharedFlow so the View handles framework calls itself
 *    (per `android-viewmodel` skill: state via StateFlow, events via
 *    SharedFlow).
 */
@HiltViewModel
class TranslationReaderViewModel @Inject constructor(
    private val arabicRepo: ArabicVerseRepository,
    private val transliterationRepo: TransliterationRepository,
    private val translationRepo: TranslationRepository,
    private val readerModeRepo: ReaderModeRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val userPreferences: UserPreferences,
    private val positionMapper: PositionMapper,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Per-verse row driving [VerseCard]. `@Immutable` annotation lives in the UI. */
    data class VerseRow(
        val surah: Int,
        val ayah: Int,
        val arabic: String,
        val transliteration: String?,
        val translation: String?,
        val isSurahStart: Boolean,
    )

    data class JuzUiState(
        val juz: Int = 1,
        val rows: List<VerseRow> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    /** One-shot UI intents (navigation / audio / share). */
    sealed interface Intent {
        /** Reuse existing page-based playback in [AudioViewModel.playPage]. */
        data class PlayPage(val page: Int) : Intent
        data class ShareAyah(val surah: Int, val ayah: Int, val text: String) : Intent
        data class JumpToMushaf(val surah: Int, val ayah: Int) : Intent
    }

    private val initialJuz: Int = (savedState.get<Int>(ARG_JUZ) ?: 1).coerceIn(1, 30)

    private val _currentJuz = MutableStateFlow(initialJuz)
    val currentJuz: StateFlow<Int> = _currentJuz.asStateFlow()

    private val _juzState = MutableStateFlow(JuzUiState(juz = initialJuz))
    val juzState: StateFlow<JuzUiState> = _juzState.asStateFlow()

    private val _intents = MutableSharedFlow<Intent>(replay = 0, extraBufferCapacity = 1)
    val intents: SharedFlow<Intent> = _intents.asSharedFlow()

    init {
        loadJuz(initialJuz)
    }

    /** Switch to a different juz. No-op if already loading the same juz. */
    fun selectJuz(juz: Int) {
        val coerced = juz.coerceIn(1, 30)
        if (_currentJuz.value == coerced && _juzState.value.juz == coerced && !_juzState.value.isLoading) return
        _currentJuz.value = coerced
        loadJuz(coerced)
    }

    /** Persist [surah]:[ayah] as the last cross-mode anchor. */
    fun rememberPosition(surah: Int, ayah: Int) {
        viewModelScope.launch {
            readerModeRepo.setLastPosition(surah, ayah)
        }
    }

    fun playAyah(surah: Int, ayah: Int) {
        viewModelScope.launch {
            // The audio engine plays whole pages today; jump to the
            // page that contains the ayah and let it stream from there.
            val page = positionMapper.surahAyahToPage(surah, ayah)
            _intents.emit(Intent.PlayPage(page))
        }
    }

    fun toggleBookmark(surah: Int, ayah: Int) {
        viewModelScope.launch {
            val page = positionMapper.surahAyahToPage(surah, ayah)
            val existing = bookmarkRepo.findBookmarkByAyah(page, surah, ayah)
            if (existing == null) {
                bookmarkRepo.addBookmark(page = page, surah = surah, ayah = ayah)
            } else {
                bookmarkRepo.removeBookmark(existing)
            }
        }
    }

    fun shareAyah(row: VerseRow) {
        viewModelScope.launch {
            val text = buildString {
                append(row.arabic)
                if (!row.translation.isNullOrBlank()) {
                    append('\n').append('\n').append(row.translation)
                }
                append('\n').append("(").append(row.surah).append(':').append(row.ayah).append(")")
            }
            _intents.emit(Intent.ShareAyah(row.surah, row.ayah, text))
        }
    }

    fun jumpToMushaf(surah: Int, ayah: Int) {
        viewModelScope.launch { _intents.emit(Intent.JumpToMushaf(surah, ayah)) }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun loadJuz(juz: Int) {
        _juzState.update { it.copy(juz = juz, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val arabic: List<ArabicVerse> = arabicRepo.getJuz(juz)
                val editionId = userPreferences.translationEditionId.first()
                val rows = arabic.map { v ->
                    val translation = translationRepo.getTranslation(v.surahNumber, v.ayahNumber, editionId)?.text
                    val transliteration = transliterationRepo
                        .getRange(v.surahNumber, v.ayahNumber, v.ayahNumber)
                        .firstOrNull()?.text
                    VerseRow(
                        surah = v.surahNumber,
                        ayah = v.ayahNumber,
                        arabic = v.textUthmani,
                        transliteration = transliteration,
                        translation = translation,
                        isSurahStart = v.ayahNumber == 1,
                    )
                }
                _juzState.update { it.copy(rows = rows, isLoading = false) }
                // Surface "this juz has no Arabic" cleanly rather than
                // showing a blank screen — the bundled fixture only
                // covers Al-Fatihah 1..3 right now.
                if (rows.isEmpty()) {
                    _juzState.update { it.copy(error = "Arabic text not yet bundled for juz $juz") }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _juzState.update { it.copy(isLoading = false, error = e.message ?: "load failed") }
            }
        }
    }

    companion object {
        /** SavedStateHandle key — mirrors `Screen.TranslationReader` nav arg. */
        const val ARG_JUZ = "juz"

        /** Build the start-juz from a `(surah, ayah)` anchor. */
        fun juzFor(surah: Int, ayah: Int): Int = QuranInfo.juzOf(surah, ayah)
    }
}
