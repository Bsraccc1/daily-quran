package com.quranreader.custom.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.local.AyahCoordinateDao
import com.quranreader.custom.data.model.AyahCoordinate
import com.quranreader.custom.data.model.HighlightedAyah
import com.quranreader.custom.data.model.QuranPage
import com.quranreader.custom.data.preferences.ReaderOrientation
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.BookmarkRepository
import com.quranreader.custom.data.repository.QuranRepository
import com.quranreader.custom.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SessionState {
    IDLE,           // No active session
    INPUT_PENDING,  // User clicked "Start Session", input visible
    ACTIVE,         // Session running
    COMPLETED       // Session complete (sheet shown)
}

/**
 * State of the manual save chip surfaced in the reader's floating
 * top-right pill. The reader shows the chip as a tappable
 * `Save`-icon button by default; right after a successful manual
 * save fires (user tapped it), the chip flips to a `CheckCircle`
 * with a localised "Saved" / "Tersimpan" label for ≈ 1.5 s, then
 * relaxes back to [Idle].
 *
 * Manual-save replaces the previous auto-save indicator (BY_TIME
 * dwell + BY_PAGES counter) — both removed in v10.x because the
 * auto debounce was racing real progress when the user navigated
 * backwards inside a session, silently regressing `pagesRead`.
 * The save now requires an explicit user tap, which makes the
 * commit unambiguous and the bug class impossible.
 */
sealed class SaveState {
    /**
     * Default chip — the user hasn't saved recently or the Saved
     * flash has already faded. Chip renders as a tappable Save
     * icon and stays interactive.
     */
    object Idle : SaveState()

    /**
     * Brief confirmation flash right after the user taps Save and
     * the DataStore commit returns. Auto-relaxes to [Idle] after
     * the flash window so the chip re-arms for the next save.
     */
    object Saved : SaveState()
}

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val ayahCoordinateDao: AyahCoordinateDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Ayah highlight state
    private val _highlightedAyah = MutableStateFlow<HighlightedAyah?>(null)
    val highlightedAyah: StateFlow<HighlightedAyah?> = _highlightedAyah.asStateFlow()

    private val _ayahCoordinates = MutableStateFlow<List<AyahCoordinate>>(emptyList())
    val ayahCoordinates: StateFlow<List<AyahCoordinate>> = _ayahCoordinates.asStateFlow()

    private val _showAyahDialog = MutableStateFlow(false)
    val showAyahDialog: StateFlow<Boolean> = _showAyahDialog.asStateFlow()

    /**
     * Reactive bookmark state for the *currently highlighted* ayah. The
     * Mushaf reader's slide-up action panel binds to this so the
     * bookmark icon updates instantly when the user (a) picks a new
     * ayah, or (b) toggles the bookmark from elsewhere (e.g. the
     * Bookmarks tab). [flatMapLatest] is the right combinator because
     * it cancels the previous DB subscription as soon as the highlight
     * changes — there's no risk of a stale row leaking through.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val highlightedAyahBookmarked: StateFlow<Boolean> = _highlightedAyah
        .flatMapLatest { ayah ->
            if (ayah == null) flowOf(false)
            else bookmarkRepository.observeAyahBookmarked(
                page = ayah.page,
                surah = ayah.surah,
                ayah = ayah.ayah,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    // F-06: UI visibility for fullscreen mode
    private val _isUiVisible = MutableStateFlow(true)
    val isUiVisible: StateFlow<Boolean> = _isUiVisible.asStateFlow()

    /**
     * Reader orientation override. Backed by [UserPreferences] so the
     * choice survives configuration changes and process death. The
     * reader's swipe-up panel surfaces a toggle button that cycles
     * through AUTO → PORTRAIT → LANDSCAPE so the user can lock the
     * mushaf into landscape (zoomed view) without flipping their
     * device's global rotation lock.
     */
    val readerOrientation: StateFlow<ReaderOrientation> = userPreferences.readerOrientation
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ReaderOrientation.AUTO,
        )

    fun cycleReaderOrientation() {
        viewModelScope.launch {
            val next = when (readerOrientation.value) {
                ReaderOrientation.AUTO -> ReaderOrientation.PORTRAIT
                ReaderOrientation.PORTRAIT -> ReaderOrientation.LANDSCAPE
                ReaderOrientation.LANDSCAPE -> ReaderOrientation.AUTO
            }
            userPreferences.setReaderOrientation(next)
        }
    }

    fun setReaderOrientation(orientation: ReaderOrientation) {
        viewModelScope.launch { userPreferences.setReaderOrientation(orientation) }
    }

    // F-02: Go to Page dialog state
    private val _showGoToPageDialog = MutableStateFlow(false)
    val showGoToPageDialog: StateFlow<Boolean> = _showGoToPageDialog.asStateFlow()

    // ── Continue Reading session management ──────────────────────────────────
    // Session state
    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // The new session limit from DataStore (default 5)
    val newSessionLimit = userPreferences.newSessionLimit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 5
        )

    // The continue reading limit from DataStore (default 2)
    val continueReadingLimit = userPreferences.continueReadingLimit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 2
        )

    // The page where the current auto-session started
    private val _sessionStartPage = MutableStateFlow<Int?>(null)
    val sessionStartPage: StateFlow<Int?> = _sessionStartPage.asStateFlow()

    // Flag to track if we've navigated to mushaf for current session
    private val _hasNavigatedToMushaf = MutableStateFlow(false)
    val hasNavigatedToMushaf: StateFlow<Boolean> = _hasNavigatedToMushaf.asStateFlow()

    /**
     * Last page the active session covers — *inclusive*.
     *
     * Earlier revisions stored the legacy "first page after the
     * session" sentinel (`startPage + targetPages`), which made the
     * indicator over-count by one and silently shifted the auto-stop
     * sheet a page late. Inclusive math mirrors how the user reads
     * the dialog ("Pages 1–10 = ten pages"): on page 10 the indicator
     * shows 10/10, the sheet has not fired yet, and flipping forward
     * to page 11 is what triggers the completion bottom-sheet.
     */
    private val _sessionEndPage = MutableStateFlow<Int?>(null)

    // Whether the session complete sheet should be shown
    private val _showSessionCompleteSheet = MutableStateFlow(false)
    val showSessionCompleteSheet: StateFlow<Boolean> = _showSessionCompleteSheet.asStateFlow()

    /**
     * Pages read in the active auto-session, capped at the inclusive
     * range size so the indicator never reports "11 of 10" after the
     * user nudges past the last page (the auto-stop sheet appears
     * the moment they do, but the chip should still read 10/10
     * which is what the user expects when leaving and resuming).
     */
    val pagesReadInSession: StateFlow<Int> = combine(
        _currentPage,
        _sessionStartPage,
        _sessionEndPage,
    ) { current, start, end ->
        if (start == null || end == null) return@combine 0
        val total = (end - start + 1).coerceAtLeast(1)
        (current - start + 1).coerceIn(0, total)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Session start timestamp for duration tracking
    private var sessionStartTimeMs: Long = 0L

    // Legacy session support (for multi-session screen)
    val isSessionActive = userPreferences.isSessionActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val legacySessionStartPage = userPreferences.sessionStartPage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1
        )

    val sessionTargetPages = userPreferences.sessionTargetPages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _showSessionCompleteDialog = MutableStateFlow(false)
    val showSessionCompleteDialog: StateFlow<Boolean> = _showSessionCompleteDialog.asStateFlow()

    val legacyPagesReadInSession: StateFlow<Int> = combine(
        _currentPage,
        legacySessionStartPage
    ) { current, start ->
        if (isSessionActive.value) {
            current - start + 1
        } else {
            0
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Track last page for statistics
    private var lastTrackedPage: Int? = null

    /**
     * Live UI state for the manual save chip. Flips to
     * [SaveState.Saved] for [SAVED_FLASH_MS] right after the user
     * taps Save and the DataStore commit returns, then back to
     * [SaveState.Idle].
     *
     * Driven exclusively by [saveSessionProgress] — there is no
     * background timer feeding it, which is the whole point of the
     * manual-save redesign: the chip can never claim a save fired
     * unless the user actually triggered one.
     */
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * Persist the active session's pagesRead. The actual monotonic +
     * cap rules live in [UserPreferences.updateSessionProgress] /
     * [SessionProgressMath], so this function only needs to compute
     * the candidate raw value and forward it.
     */
    private suspend fun persistSessionProgress(page: Int) {
        val activeSessionId = userPreferences.activeSessionId.first() ?: return
        val sessions = userPreferences.sessions.first()
        val activeSession = sessions.find { it.id == activeSessionId } ?: return
        val raw = (page - activeSession.startPage + 1).coerceAtLeast(0)
        userPreferences.updateSessionProgress(raw)
    }

    /**
     * **Manual save** — commit the current page's session progress
     * to DataStore. Called from the reader's tappable Save chip;
     * this is the **only** path that updates `pagesRead`.
     *
     * Behaviour:
     *  1. Read [_currentPage] synchronously (so a concurrent flip
     *     during the click can't race us).
     *  2. Launch on [NonCancellable] so the DataStore edit
     *     completes even if the user back-navigates mid-write.
     *  3. Persist `pagesRead` (monotonic — see
     *     [com.quranreader.custom.data.preferences.SessionProgressMath])
     *     and `lastPage` so the Dashboard's "Continue Reading"
     *     stays in sync with the just-committed mark.
     *  4. Flip [saveState] to `Saved` for [SAVED_FLASH_MS] then
     *     back to `Idle` so the chip re-arms.
     *
     * Idempotent — tapping twice in a row at the same page is fine;
     * the second commit is a no-op at the DataStore layer
     * (watermark unchanged) and the chip just re-flashes Saved.
     */
    fun saveSessionProgress() {
        val page = _currentPage.value
        viewModelScope.launch(NonCancellable) {
            persistSessionProgress(page)
            userPreferences.setLastPage(page)
            _saveState.value = SaveState.Saved
            delay(SAVED_FLASH_MS)
            _saveState.value = SaveState.Idle
        }
    }

    private companion object {
        /** How long the chip flashes "Saved" after a manual commit. */
        const val SAVED_FLASH_MS = 1_500L
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Called when the pager settles on a new page. Updates the
     * in-memory current-page flow, persists the `lastPage` pointer
     * (so cold-start "Continue Reading" remembers where the user
     * was), and bumps the daily-pages-read statistic.
     *
     * Crucially, this **does not** persist session `pagesRead` —
     * that's reserved for [saveSessionProgress] (manual Save
     * button) so navigating around inside the reader can't
     * silently rewrite the session's high-water mark.
     */
    fun setCurrentPage(page: Int) {
        safeLaunch(
            onError = { _errorMessage.value = "Failed to save page progress" }
        ) {
            _currentPage.value = page
            userPreferences.setLastPage(page)

            // Track page change for statistics
            if (lastTrackedPage != page) {
                userPreferences.incrementPagesRead()
                lastTrackedPage = page
            }

            // Session `pagesRead` is committed only when the user
            // taps the manual Save chip — see [saveSessionProgress].
            // This is the deliberate behaviour change in v10.x: no
            // background debounce can ever overwrite the user's mark.

            // ── Auto-session: check limit ──
            // The session covers pages [startPage..endPage] inclusive
            // ("Pages 1–10 = ten pages"). The completion sheet fires
            // the moment the user nudges *past* endPage so the
            // 10th page reads as the natural finish line and the
            // sheet is the visible "OK, you're done" cue. The
            // `page > start` guard prevents the sheet from popping
            // open when something seeds `_currentPage` before the
            // session fully arms.
            val start = _sessionStartPage.value
            val end = _sessionEndPage.value
            if (end != null && start != null && _sessionState.value == SessionState.ACTIVE) {
                if (page > end && page > start && !_showSessionCompleteSheet.value) {
                    _showSessionCompleteSheet.value = true
                    _sessionState.value = SessionState.COMPLETED
                }
            }

            // Load ayah coordinates (empty list if not available)
            val coords = try {
                ayahCoordinateDao.getCoordinatesForPage(page)
            } catch (e: Exception) {
                Log.w("ReadingViewModel", "No coordinates for page $page", e)
                emptyList()
            }
            _ayahCoordinates.value = coords

            // Check if bookmarked
            _isBookmarked.value = bookmarkRepository.isPageBookmarked(page)
        }
    }

    // ── Auto-session actions ─────────────────────────────────────────────────

    /** Show input for starting a new session */
    fun showStartSessionInput() {
        _sessionState.value = SessionState.INPUT_PENDING
    }

    /** Start a new session with specified target pages */
    fun startNewSession(targetPages: Int) {
        // Guard: prevent starting a new session if one is already active
        if (_sessionState.value == SessionState.ACTIVE) {
            return
        }

        val current = _currentPage.value
        val pages = targetPages.coerceAtLeast(1)
        _sessionStartPage.value = current
        // Inclusive last page — "start at 5, target 10" runs 5..14.
        _sessionEndPage.value = (current + pages - 1).coerceAtMost(604)
        _sessionState.value = SessionState.ACTIVE
        _hasNavigatedToMushaf.value = false // Reset navigation flag for new session
        sessionStartTimeMs = System.currentTimeMillis()
    }

    /**
     * Start a session with an explicit [startPage] and [targetPages] —
     * deterministic counterpart to [startNewSession] used by the
     * Session and Dashboard tabs. The legacy variant reads
     * `_currentPage`, which races with the pager's first
     * `setCurrentPage(...)` emission and previously caused multi-session
     * starts to use the wrong page (the user reported "target=10 but I'm
     * limited to 6 pages forward" because `_currentPage` was still 1
     * when the auto-start fired). Passing both values explicitly keeps
     * the limit math anchored to what the user picked, regardless of
     * Flow timing.
     *
     * Idempotent: re-entering the reader with the **same** session
     * parameters is a no-op (back-navigation shouldn't reset the
     * session timer or the completion sheet). Re-entering with
     * **different** parameters re-arms — that's how the Session
     * tab lets the user switch between sessions without first
     * clearing the previously active one.
     */
    fun startSessionWithStart(startPage: Int, targetPages: Int) {
        val safeStart = startPage.coerceIn(1, 604)
        val pages = targetPages.coerceAtLeast(1)
        // Inclusive last page ("Pages X–(X+pages-1)"). Capped at the
        // mushaf's last real page.
        val newEnd = (safeStart + pages - 1).coerceIn(safeStart, 604)

        if (_sessionState.value == SessionState.ACTIVE &&
            _sessionStartPage.value == safeStart &&
            _sessionEndPage.value == newEnd
        ) {
            // Same session as last time — leave the duration timer
            // and completion-sheet flag alone.
            return
        }

        _sessionStartPage.value = safeStart
        _sessionEndPage.value = newEnd
        _sessionState.value = SessionState.ACTIVE
        _hasNavigatedToMushaf.value = false
        _showSessionCompleteSheet.value = false
        sessionStartTimeMs = System.currentTimeMillis()
    }

    /** Mark that we've navigated to mushaf */
    fun markNavigatedToMushaf() {
        _hasNavigatedToMushaf.value = true
    }

    /** Show input for continuing session */
    fun showContinueSessionInput() {
        _sessionState.value = SessionState.INPUT_PENDING
    }

    /** "Continue Reading" — rolling extension by the user's configured limit. */
    fun continueReadingSession() {
        extendSessionBy(continueReadingLimit.value)
    }

    /** Continue session with custom pages */
    fun continueSessionWithPages(additionalPages: Int) {
        extendSessionBy(additionalPages)
    }

    /**
     * Shared backing for the two Continue-Reading entry points.
     * Extends the inclusive end-page by [additionalPages] and
     * mirrors the change into the active multi-session entry's
     * `targetPages` so the dashboard / Session-tab progress bar
     * doesn't get stuck at "10/10 ✓" while the reader is happily
     * tracking 11/12.
     */
    private fun extendSessionBy(additionalPages: Int) {
        val extension = additionalPages.coerceAtLeast(1)
        val end = _sessionEndPage.value
        _sessionEndPage.value = ((end ?: _currentPage.value) + extension).coerceAtMost(604)
        _showSessionCompleteSheet.value = false
        _sessionState.value = SessionState.ACTIVE
        viewModelScope.launch {
            // Keep the legacy single-session key in sync (limit math
            // for older code paths) AND the new multi-session entry.
            userPreferences.extendSession(extension)
            userPreferences.extendActiveSession(extension)
        }
    }

    /**
     * "Close" — terminate the active session.
     *
     * Two-phase: in-memory state is cleared synchronously so the
     * reader's chrome (completion sheet, progress chip) hides on
     * the next frame, then the DataStore mutations are launched on
     * [viewModelScope] so the multi-session list, active-session id,
     * and legacy single-session flag all reflect the close after
     * the next DataStore commit.
     *
     * Pre-fix bug: previously this only cleared the in-memory
     * StateFlows, so on the next app launch the Dashboard still
     * read `activeSessionId != null` and `isActive=true` from
     * DataStore — "Continue Reading" jumped back into the closed
     * session, exactly the "kereset ga nyimpan" symptom the user
     * reported.
     */
    fun closeSession() {
        _showSessionCompleteSheet.value = false
        _sessionState.value = SessionState.IDLE
        // Reset so a new session can be started
        _sessionStartPage.value = null
        _sessionEndPage.value = null
        _hasNavigatedToMushaf.value = false
        viewModelScope.launch {
            // Mark the active multi-session entry as completed (kept
            // visible in the Session tab as a finished card) and
            // detach the active-id pointer so a fresh session can
            // arm without colliding with the now-closed one.
            val activeId = userPreferences.activeSessionId.first()
            if (activeId != null) {
                val sessions = userPreferences.sessions.first()
                sessions.find { it.id == activeId }?.let { session ->
                    userPreferences.updateSession(
                        session.copy(isActive = false, isCompleted = true),
                    )
                }
                userPreferences.setActiveSession(null)
            }
            // Drop the legacy single-session flag too so older code
            // paths (HomeViewModel.isSessionActive, the limit math
            // in setCurrentPage) match the new state.
            userPreferences.endSession()
        }
    }

    /** Cancel input and return to previous state */
    fun cancelSessionInput() {
        _sessionState.value = if (_sessionStartPage.value != null) {
            SessionState.ACTIVE
        } else {
            SessionState.IDLE
        }
    }

    /** Duration in minutes since session started */
    fun sessionDurationMinutes(): Int {
        if (sessionStartTimeMs == 0L) return 0
        return ((System.currentTimeMillis() - sessionStartTimeMs) / 60_000).toInt()
    }

    // F-06: Toggle UI visibility
    fun toggleUiVisibility() {
        _isUiVisible.value = !_isUiVisible.value
    }

    // F-02: Go to Page dialog
    fun showGoToPageDialog() {
        _showGoToPageDialog.value = true
    }

    fun hideGoToPageDialog() {
        _showGoToPageDialog.value = false
    }

    // Legacy session management (for multi-session screen compat)
    fun showSessionCompleteDialog() {
        _showSessionCompleteDialog.value = true
    }

    fun hideSessionCompleteDialog() {
        _showSessionCompleteDialog.value = false
    }

    fun continueSession() {
        _showSessionCompleteDialog.value = false
    }

    fun endSession() {
        viewModelScope.launch {
            userPreferences.endSession()
        }
    }

    // Bookmark management
    fun toggleBookmark() {
        safeLaunch(
            onError = { _errorMessage.value = "Failed to toggle bookmark" }
        ) {
            val page = _currentPage.value
            if (_isBookmarked.value) {
                bookmarkRepository.removeBookmarkByPage(page)
                _isBookmarked.value = false
            } else {
                bookmarkRepository.addBookmark(page)
                _isBookmarked.value = true
            }
        }
    }

    // Ayah interaction (long-press path — also opens the ayah action dialog).
    fun onAyahTapped(page: Int, surah: Int, ayah: Int) {
        safeLaunch {
            val isBookmarked = bookmarkRepository.findBookmarkByAyah(page, surah, ayah) != null
            _highlightedAyah.value = HighlightedAyah(
                page = page,
                surah = surah,
                ayah = ayah,
                isBookmarked = isBookmarked,
            )
            _showAyahDialog.value = true
        }
    }

    /**
     * Toggle a bookmark on the *currently highlighted* ayah. Keys off
     * the precise (page, surah, ayah) triple so per-ayah bookmarks on
     * the same page are independent — toggling 2:255 will never affect
     * 2:256, and neither will affect a page-level bookmark on the
     * same page (where surah/ayah are NULL in the schema).
     *
     * The reactive [highlightedAyahBookmarked] flow is the source of
     * truth for the icon state in the slide-up panel; this function
     * just flips the underlying row.
     */
    fun toggleAyahBookmark() {
        safeLaunch(
            onError = { _errorMessage.value = "Failed to toggle bookmark" }
        ) {
            val ayah = _highlightedAyah.value ?: return@safeLaunch
            val existing = bookmarkRepository.findBookmarkByAyah(
                page = ayah.page,
                surah = ayah.surah,
                ayah = ayah.ayah,
            )
            if (existing != null) {
                bookmarkRepository.removeBookmarkByAyah(ayah.page, ayah.surah, ayah.ayah)
                _highlightedAyah.value = ayah.copy(isBookmarked = false)
            } else {
                bookmarkRepository.addBookmark(ayah.page, ayah.surah, ayah.ayah)
                _highlightedAyah.value = ayah.copy(isBookmarked = true)
            }
        }
    }

    fun clearHighlight() {
        _highlightedAyah.value = null
        _showAyahDialog.value = false
    }

    /**
     * Pre-highlight an ayah without showing the action dialog. Used by
     *  - the "search by surah + ayah" flow so the user lands on the page
     *    with the verse already lit up; and
     *  - any tap on a glyph in the Mushaf renderer (drives the
     *    slide-down + slide-up panels via [highlightedAyah]).
     *
     * Skips a no-op set when the same ayah is already highlighted
     * (e.g. from a previous tap) so we don't fight a user who has
     * already moved on.
     */
    fun setInitialHighlight(page: Int, surah: Int, ayah: Int) {
        if (surah <= 0 || ayah <= 0) return
        val current = _highlightedAyah.value
        if (current?.page == page && current.surah == surah && current.ayah == ayah) return
        safeLaunch {
            // Look up the *exact* per-ayah row, not the first page-level
            // bookmark. The reactive flow takes over from here, so this
            // is just a one-shot seed for the very first frame.
            val isBookmarked = bookmarkRepository.findBookmarkByAyah(page, surah, ayah) != null
            _highlightedAyah.value = HighlightedAyah(
                page = page,
                surah = surah,
                ayah = ayah,
                isBookmarked = isBookmarked,
            )
        }
    }
}
