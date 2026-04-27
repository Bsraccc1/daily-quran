package com.quranreader.custom.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.local.AyahCoordinateDao
import com.quranreader.custom.data.model.AyahCoordinate
import com.quranreader.custom.data.model.HighlightedAyah
import com.quranreader.custom.data.model.QuranPage
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.BookmarkRepository
import com.quranreader.custom.data.repository.QuranRepository
import com.quranreader.custom.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

enum class SessionState {
    IDLE,           // No active session
    INPUT_PENDING,  // User clicked "Start Session", input visible
    ACTIVE,         // Session running
    COMPLETED       // Session complete (sheet shown)
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

    // Rolling target page: startPage + limit, extended on "Continue Reading"
    private val _sessionTargetPage = MutableStateFlow<Int?>(null)

    // Whether the session complete sheet should be shown
    private val _showSessionCompleteSheet = MutableStateFlow(false)
    val showSessionCompleteSheet: StateFlow<Boolean> = _showSessionCompleteSheet.asStateFlow()

    // Pages read in current auto-session
    val pagesReadInSession: StateFlow<Int> = combine(
        _currentPage,
        _sessionStartPage
    ) { current, start ->
        // FIX: Calculate pages read correctly
        // If start=17, current=17, pages read = 1 (not 0)
        // If start=17, current=27, pages read = 11 (not 10)
        if (start != null) (current - start + 1).coerceAtLeast(0) else 0
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

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Called when pager settles on a new page. Handles auto-session tracking.
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

            // ── Update multi-session pagesRead (for Session tab sync) ──
            // Get active session from SessionViewModel and update its progress
            val activeSessionId = userPreferences.activeSessionId.first()
            if (activeSessionId != null) {
                val sessions = userPreferences.sessions.first()
                val activeSession = sessions.find { it.id == activeSessionId }
                if (activeSession != null) {
                    val pagesRead = (page - activeSession.startPage + 1).coerceAtLeast(0)
                    userPreferences.updateSessionProgress(pagesRead)
                }
            }

            // ── Auto-session: check limit ──
            val start = _sessionStartPage.value
            val target = _sessionTargetPage.value
            if (target != null && start != null && _sessionState.value == SessionState.ACTIVE) {
                // FIX: Only show completion if we've actually read pages (page > start)
                // and reached the target. This prevents spam when start page is set incorrectly.
                if (page >= target && page > start && !_showSessionCompleteSheet.value) {
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
        _sessionStartPage.value = current
        // FIX: Ensure target is always ahead of current page
        _sessionTargetPage.value = current + targetPages.coerceAtLeast(1)
        _sessionState.value = SessionState.ACTIVE
        _hasNavigatedToMushaf.value = false // Reset navigation flag for new session
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

    /** "Continue Reading" — rolling extension */
    fun continueReadingSession() {
        val current = _currentPage.value
        // FIX: Ensure we're extending from current position, not going backwards
        _sessionTargetPage.value = current + continueReadingLimit.value.coerceAtLeast(1)
        _showSessionCompleteSheet.value = false
        _sessionState.value = SessionState.ACTIVE
    }

    /** Continue session with custom pages */
    fun continueSessionWithPages(additionalPages: Int) {
        val current = _currentPage.value
        // FIX: Ensure positive extension
        _sessionTargetPage.value = current + additionalPages.coerceAtLeast(1)
        _showSessionCompleteSheet.value = false
        _sessionState.value = SessionState.ACTIVE
    }

    /** "Close" — terminate session */
    fun closeSession() {
        _showSessionCompleteSheet.value = false
        _sessionState.value = SessionState.IDLE
        // Reset so a new session can be started
        _sessionStartPage.value = null
        _sessionTargetPage.value = null
        _hasNavigatedToMushaf.value = false
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
