package com.quranreader.custom.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.local.AyahCoordinateDao
import com.quranreader.custom.data.model.AyahCoordinate
import com.quranreader.custom.data.model.HighlightedAyah
import com.quranreader.custom.data.model.QuranPage
import com.quranreader.custom.data.preferences.AutoSaveMode
import com.quranreader.custom.data.preferences.ReaderOrientation
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.BookmarkRepository
import com.quranreader.custom.data.repository.QuranRepository
import com.quranreader.custom.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

enum class SessionState {
    IDLE,           // No active session
    INPUT_PENDING,  // User clicked "Start Session", input visible
    ACTIVE,         // Session running
    COMPLETED       // Session complete (sheet shown)
}

/**
 * State of the auto-save indicator surfaced in the reader's
 * floating top-right chip. The reader shows a small pill that
 * mirrors whichever case is currently active so the user can see
 *   - **how long / how many pages** until their progress is committed,
 *   - **when** the commit fires (brief check-mark flash), and
 *   - **idle** the rest of the time once the previous save settled.
 *
 * The trigger is driven by [UserPreferences.autoSaveMode]:
 *  - [AutoSaveMode.BY_TIME] uses [UserPreferences.autoSavePageSeconds]
 *    as a dwell countdown, so [Counting.secondsLeft] is literally
 *    seconds and [Counting.byPages] is `false`.
 *  - [AutoSaveMode.BY_PAGES] uses [UserPreferences.autoSavePageCount]
 *    as a flip-count threshold; `secondsLeft` then carries
 *    *pages-until-save* and [Counting.byPages] is `true` so the chip
 *    can render a "3 pages" / "3 hlm" label instead of "3s".
 *
 * Both modes are surfaced in Settings → Reading so users with anxious
 * save habits can shorten it, batch readers can switch to BY_PAGES,
 * and battery-conscious users can lengthen either.
 */
sealed class AutoSaveTick {
    /** No countdown in flight. The chip is hidden by the caller. */
    object Idle : AutoSaveTick()

    /**
     * Countdown is running. [progress] climbs from 0 → 1 over the
     * configured window so the chip can render a determinate ring;
     * [secondsLeft] is the units-left number the chip renders next
     * to the icon. [byPages] swaps the localised template the chip
     * picks ("3s" vs "3 pages") so we don't need two parallel
     * ticker types.
     */
    data class Counting(
        val progress: Float,
        val secondsLeft: Int,
        val byPages: Boolean = false,
    ) : AutoSaveTick()

    /** Brief flash right after a successful persist (≈ 1.5 s). */
    object Saved : AutoSaveTick()
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
     * User-configurable dwell window before the reader commits the
     * page change to DataStore in [AutoSaveMode.BY_TIME]. Backed by
     * [UserPreferences.autoSavePageSeconds] (default 3 s, range
     * 1..60) and surfaced in Settings → Reading so users can dial it
     * to their habits.
     */
    val autoSavePageSeconds: StateFlow<Int> = userPreferences.autoSavePageSeconds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 3,
        )

    /**
     * Active auto-save trigger. BY_TIME (legacy default) debounces
     * persistence on dwell time; BY_PAGES commits every Nth page
     * flip regardless of dwell. [autoSaveTick] and the persistence
     * collector both pivot on this so the chip and the actual save
     * stay in lock-step with whatever the user picked in Settings.
     */
    val autoSaveMode: StateFlow<AutoSaveMode> = userPreferences.autoSaveMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AutoSaveMode.BY_TIME,
        )

    /**
     * Page-count threshold for [AutoSaveMode.BY_PAGES]. Range 1..50
     * enforced upstream in [UserPreferences].
     */
    val autoSavePageCount: StateFlow<Int> = userPreferences.autoSavePageCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 3,
        )

    /**
     * Wall-clock millis of the most recent [setCurrentPage] call.
     * Drives [autoSaveTick] so the chip's countdown re-syncs every
     * time the user lands on a new page — even mid-saved-flash.
     */
    private val pageChangeTimeMs = MutableStateFlow(0L)

    /**
     * How many distinct page flips the user has clocked since the
     * last successful persist. Reset to 0 inside the BY_PAGES
     * collector the moment a save fires. Drives the BY_PAGES tick
     * label ("in 2 pages…") so the user can see how close they are
     * to the next commit.
     */
    private val _pagesSinceLastSave = MutableStateFlow(0)

    /**
     * Wall-clock millis of the most recent successful persist.
     * Drives the ≈ 1.5 s "Saved" flash in both modes — the
     * BY_TIME ticker derives this implicitly from `pageChangeTimeMs +
     * totalMs`, but BY_PAGES needs an explicit signal because its
     * commit is event-driven (page count threshold), not time-based.
     */
    private val _lastSaveTimeMs = MutableStateFlow(0L)

    /**
     * Live UI state for the auto-save indicator. Mode-aware:
     *  - BY_TIME: counts down from `autoSavePageSeconds` to 0,
     *    flashes Saved at the bottom of the timer, then goes Idle.
     *  - BY_PAGES: shows a "X pages until save" countdown for ~ 2 s
     *    after every flip, flashes Saved when the threshold is hit,
     *    then goes Idle.
     *
     * Both inner flows self-terminate (return from `flow { }`) once
     * Idle is reached so we don't burn CPU once the user has been
     * parked on the same page for a while.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val autoSaveTick: StateFlow<AutoSaveTick> = combine(
        autoSaveMode,
        pageChangeTimeMs,
    ) { mode, changedAt -> mode to changedAt }
        .flatMapLatest { (mode, changedAt) ->
            if (changedAt == 0L) {
                flowOf<AutoSaveTick>(AutoSaveTick.Idle)
            } else when (mode) {
                AutoSaveMode.BY_TIME -> byTimeTickerFlow(changedAt)
                AutoSaveMode.BY_PAGES -> byPagesTickerFlow(changedAt)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AutoSaveTick.Idle,
        )

    /**
     * Time-based ticker. The explicit `<AutoSaveTick>` type
     * parameter on `flow { }` is what keeps the inferred
     * FlowCollector at the sealed parent — without it Kotlin pins
     * the flow to whichever subtype the first `emit` uses and
     * rejects the later siblings.
     */
    private fun byTimeTickerFlow(changedAt: Long): Flow<AutoSaveTick> {
        val totalMs = autoSavePageSeconds.value.toLong() * 1000L
        val savedFlashMs = 1_500L
        return flow<AutoSaveTick> {
            while (true) {
                val elapsed = System.currentTimeMillis() - changedAt
                when {
                    elapsed >= totalMs + savedFlashMs -> {
                        emit(AutoSaveTick.Idle)
                        return@flow
                    }
                    elapsed >= totalMs -> emit(AutoSaveTick.Saved)
                    else -> {
                        val progress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                        val secondsLeft = ((totalMs - elapsed + 999) / 1000).toInt()
                        emit(AutoSaveTick.Counting(progress, secondsLeft, byPages = false))
                    }
                }
                delay(100)
            }
        }
    }

    /**
     * Page-count ticker. Runs for ~ 2 s after each flip to show the
     * user how many pages remain until the next persist, then folds
     * into the Saved flash if the persist landed inside that window
     * (`_lastSaveTimeMs >= changedAt`), then Idle. The inner loop
     * polls every 100 ms so the saved-flash transition is
     * sub-second-perceptible without doing per-frame work.
     */
    private fun byPagesTickerFlow(changedAt: Long): Flow<AutoSaveTick> {
        val countingWindowMs = 2_000L
        val savedFlashMs = 1_500L
        return flow<AutoSaveTick> {
            while (true) {
                val now = System.currentTimeMillis()
                val sinceChanged = now - changedAt
                val savedAt = _lastSaveTimeMs.value
                val sinceSaved = now - savedAt
                val savedActive = savedAt > 0L && savedAt >= changedAt && sinceSaved < savedFlashMs
                when {
                    savedActive -> emit(AutoSaveTick.Saved)
                    sinceChanged < countingWindowMs -> {
                        val threshold = autoSavePageCount.value.coerceAtLeast(1)
                        val count = _pagesSinceLastSave.value.coerceIn(0, threshold)
                        // `pagesUntilSave == 0` means the threshold
                        // was hit by *this* flip but the persist
                        // hasn't landed yet — we still want to show
                        // a counting pill (not Idle) so there's no
                        // visual gap before the Saved flash.
                        val pagesUntilSave = (threshold - count).coerceAtLeast(0)
                        val progress = (count.toFloat() / threshold).coerceIn(0f, 1f)
                        emit(
                            AutoSaveTick.Counting(
                                progress = progress,
                                secondsLeft = pagesUntilSave,
                                byPages = true,
                            )
                        )
                    }
                    else -> {
                        emit(AutoSaveTick.Idle)
                        return@flow
                    }
                }
                delay(100)
            }
        }
    }

    init {
        // Mode-aware persistence collector. `collectLatest` on
        // [autoSaveMode] cancels the previous inner collector the
        // moment the user flips the picker, so switching modes mid-
        // session takes effect on the very next page flip without
        // any process restart.
        //
        // BY_TIME: classic dwell-debounced collector — same shape as
        // before. Wrapping the inner debounce in
        // `flatMapLatest(autoSavePageSeconds)` lets the user shorten
        // / lengthen the timer mid-stream too.
        //
        // BY_PAGES: counts each distinct page change (skip the
        // initial _currentPage emission via `drop(1)` so opening the
        // reader on page 50 doesn't pre-charge the counter); fires
        // [persistSessionProgress] when the threshold is met,
        // resets the counter, and flips [_lastSaveTimeMs] so the
        // tick UI flashes Saved.
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            autoSaveMode.collectLatest { mode ->
                when (mode) {
                    AutoSaveMode.BY_TIME -> {
                        autoSavePageSeconds
                            .flatMapLatest { seconds ->
                                _currentPage
                                    .debounce(seconds.toLong() * 1000L)
                                    .distinctUntilChanged()
                            }
                            .collect { page ->
                                persistSessionProgress(page)
                                _lastSaveTimeMs.value = System.currentTimeMillis()
                                _pagesSinceLastSave.value = 0
                            }
                    }
                    AutoSaveMode.BY_PAGES -> {
                        // Reset the counter whenever the user enters
                        // BY_PAGES mode — there's no point carrying a
                        // half-counted run from a previous mode
                        // change into the new threshold.
                        _pagesSinceLastSave.value = 0
                        // `_currentPage` is already a StateFlow which
                        // dedups by value, so no `distinctUntilChanged`
                        // needed (Kotlin 2.0 flagged the redundant call
                        // as an error). `drop(1)` skips the initial
                        // value emission so opening the reader on page
                        // 50 doesn't pre-charge the counter.
                        _currentPage
                            .drop(1)
                            .collect { page ->
                                val threshold = autoSavePageCount.value.coerceAtLeast(1)
                                val newCount = _pagesSinceLastSave.value + 1
                                if (newCount >= threshold) {
                                    persistSessionProgress(page)
                                    _lastSaveTimeMs.value = System.currentTimeMillis()
                                    _pagesSinceLastSave.value = 0
                                    // Intentionally NOT re-poking
                                    // [pageChangeTimeMs] here — the
                                    // tick flow's `savedActive`
                                    // check requires
                                    // `_lastSaveTimeMs >= changedAt`,
                                    // and `setCurrentPage` already
                                    // bumped `changedAt` to the page
                                    // flip's timestamp. Re-poking
                                    // would push `changedAt` past
                                    // the just-recorded save and
                                    // suppress the Saved flash. The
                                    // inner ticker re-reads
                                    // `_lastSaveTimeMs.value` every
                                    // 100 ms so the flash appears in
                                    // the next iteration.
                                } else {
                                    _pagesSinceLastSave.value = newCount
                                }
                            }
                    }
                }
            }
        }
    }

    /**
     * Persist the active session's pagesRead. Capped at the session's
     * own targetPages so an over-shoot during the auto-stop frame
     * doesn't poison the resume math (which subtracts 1 from
     * pagesRead to land the user back on the *last* page they read,
     * not the page after).
     */
    private suspend fun persistSessionProgress(page: Int) {
        val activeSessionId = userPreferences.activeSessionId.first() ?: return
        val sessions = userPreferences.sessions.first()
        val activeSession = sessions.find { it.id == activeSessionId } ?: return
        val raw = (page - activeSession.startPage + 1).coerceAtLeast(0)
        val capped = raw.coerceAtMost(activeSession.targetPages.coerceAtLeast(1))
        userPreferences.updateSessionProgress(capped)
    }

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
            // Reset the slide-down panel's auto-save countdown chip
            // so it re-runs from 0→autoSavePageSeconds for the new page.
            pageChangeTimeMs.value = System.currentTimeMillis()
            userPreferences.setLastPage(page)

            // Track page change for statistics
            if (lastTrackedPage != page) {
                userPreferences.incrementPagesRead()
                lastTrackedPage = page
            }

            // Multi-session `pagesRead` is persisted on a 5-second
            // debounce by the [init] collector so quick swipes don't
            // inflate progress. The collector uses the same
            // [_currentPage] flow we just updated above.

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

    /** "Close" — terminate session */
    fun closeSession() {
        _showSessionCompleteSheet.value = false
        _sessionState.value = SessionState.IDLE
        // Reset so a new session can be started
        _sessionStartPage.value = null
        _sessionEndPage.value = null
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
