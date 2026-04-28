package com.quranreader.custom.ui.screens.reading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import kotlinx.coroutines.launch
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase.Companion.IMAGE_HEIGHT_PX
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase.Companion.IMAGE_WIDTH_PX
import com.quranreader.custom.data.preferences.ReaderOrientation
import com.quranreader.custom.ui.components.TranslationEditionsDialog
import com.quranreader.custom.ui.components.TranslationPanel
import com.quranreader.custom.ui.components.mushaf.MushafImageRenderer
import com.quranreader.custom.ui.screens.search.AyahSearchDialog
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.TranslationViewModel

/**
 * Mushaf Reader Screen — Layer 2.
 *
 * Distraction-free Quran page viewer. The page is the only thing on
 * screen until the user **selects an ayah by tapping its calligraphy**.
 * That gesture lights the verse with the highlight system and reveals
 * two frosted-glass panels:
 *
 *  - **Slide-down panel** (top): the verse's context — surah, ayah
 *    number, page, juz. Read-only chips arranged for a quick glance.
 *  - **Slide-up panel** (bottom): the actions you can take on the
 *    selected verse — bookmark, translate, audio, memorize, plus a
 *    back button for leaving the reader.
 *
 * Tapping the page margin (or pressing system back) clears the
 * highlight, which dismisses both panels. The bookmark icon in the
 * bottom panel is keyed off the *highlighted ayah*'s row in the
 * bookmarks DB — toggling 2:255 will never affect 2:256, even when
 * both are bookmarked on the same page.
 *
 * DPI-safe sizing: both panels cap at `min(92% of window, 480 dp)`
 * width with a `≥ 280 dp` floor. Touch targets are 48 dp (Material
 * a11y minimum). Typography is `MaterialTheme.typography` so the OS
 * font-scale propagates correctly to all sizes.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MushafReaderScreen(
    initialPage: Int,
    onBack: () -> Unit = {},
    startSessionAutomatically: Boolean = false,
    /**
     * When [startSessionAutomatically] is true, anchor the session to
     * this page and run the limit math from here. Defaults to 0
     * meaning "use [initialPage]" so existing call sites (Juz,
     * Bookmark, search) still work. The Session and Dashboard tabs
     * pass these explicitly so the limit window is keyed off the
     * user's chosen start page even if the pager hasn't reported
     * back its own position yet.
     */
    sessionStartPageOverride: Int = 0,
    /**
     * When [startSessionAutomatically] is true, the number of pages
     * the session should cover. 0 means "use the user's
     * `newSessionLimit` setting" (legacy behaviour). The session
     * card and the Dashboard's "Create & Start" flow pass the actual
     * value here so a target of 10 is honored as 10, not silently
     * downgraded to 5 (or whatever `newSessionLimit` is set to).
     */
    sessionTargetPagesOverride: Int = 0,
    // When the user enters this screen via the "search by surah + ayah"
    // flow we receive the verse coordinates here and pre-highlight the
    // matching ayah on first composition. Both default to 0 which means
    // "no pre-highlight" so opening the reader from any other entry
    // point (Juz, Bookmark, Continue Reading, …) keeps its old behaviour.
    initialHighlightSurah: Int = 0,
    initialHighlightAyah: Int = 0,
    readingViewModel: ReadingViewModel = hiltViewModel(),
    audioViewModel: AudioViewModel = hiltViewModel(),
    translationViewModel: TranslationViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, 603),
        pageCount = { 604 },
    )

    val highlightedAyah by readingViewModel.highlightedAyah.collectAsStateWithLifecycle()
    val highlightedAyahBookmarked by
        readingViewModel.highlightedAyahBookmarked.collectAsStateWithLifecycle()
    val errorMessage by readingViewModel.errorMessage.collectAsStateWithLifecycle()
    val showSessionCompleteSheet by
        readingViewModel.showSessionCompleteSheet.collectAsStateWithLifecycle()
    val pagesReadInSession by readingViewModel.pagesReadInSession.collectAsStateWithLifecycle()
    val sessionTargetPages by readingViewModel.sessionTargetPages.collectAsStateWithLifecycle()
    val newSessionLimit by readingViewModel.newSessionLimit.collectAsStateWithLifecycle()

    // Audio state
    val audioState by audioViewModel.playbackState.collectAsStateWithLifecycle()
    val currentAudioAyah by audioViewModel.currentAyah.collectAsStateWithLifecycle()

    // Translation state — edition-aware (v9+)
    val showTranslationPanel by translationViewModel.showTranslationPanel.collectAsStateWithLifecycle()
    val translations by translationViewModel.translations.collectAsStateWithLifecycle()
    val translationLoading by translationViewModel.isLoading.collectAsStateWithLifecycle()
    val translationEditionId by translationViewModel.translationEditionId.collectAsStateWithLifecycle()
    val translationScope by translationViewModel.translationScope.collectAsStateWithLifecycle()
    val translationHighlight by translationViewModel.highlightedAyahNumber.collectAsStateWithLifecycle()
    val isCurrentEditionInstalled by translationViewModel.isCurrentEditionInstalled.collectAsStateWithLifecycle()
    val editions by translationViewModel.editions.collectAsStateWithLifecycle()
    val downloadProgressMap by translationViewModel.downloadProgress.collectAsStateWithLifecycle()
    val catalogueRefreshing by translationViewModel.catalogueRefreshing.collectAsStateWithLifecycle()
    val readerOrientation by readingViewModel.readerOrientation.collectAsStateWithLifecycle()

    val currentEdition = remember(editions, translationEditionId) {
        editions.firstOrNull { it.editionId == translationEditionId }
    }

    val currentPageNumber = pagerState.currentPage + 1

    // v3.0: state for memorization overlay
    var showMemorizeOverlay by remember { mutableStateOf(false) }

    // v9: editions catalogue dialog (download / pick / delete)
    var showEditionsDialog by remember { mutableStateOf(false) }

    // Unified navigate dialog (merges Surah+Ayah and Page jump).
    var showNavigateDialog by remember { mutableStateOf(false) }

    // Coroutine scope for pager scrollToPage() side effects from
    // the jump dialogs. Tied to this composable so the scroll is
    // cancelled if the user navigates away mid-animation.
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Update current page when pager changes — pushes into both view
    // models so the translation panel reloads automatically.
    LaunchedEffect(pagerState.currentPage) {
        readingViewModel.setCurrentPage(pagerState.currentPage + 1)
        translationViewModel.setCurrentPage(pagerState.currentPage + 1)
    }

    // Mirror the highlighted ayah into the translation VM whenever it
    // changes, so the panel can either filter (HIGHLIGHTED_ONLY) or
    // accent (ENTIRE_PAGE) the matching row.
    LaunchedEffect(highlightedAyah?.surah, highlightedAyah?.ayah) {
        translationViewModel.setHighlightedAyah(
            highlightedAyah?.surah,
            highlightedAyah?.ayah,
        )
    }

    // Apply the user's orientation override at the Activity level. The
    // reader is the only screen that supports landscape (the rest of
    // the app is a portrait-locked phone UI by manifest). When the
    // user backs out of the reader the DisposableEffect's onDispose
    // restores the manifest default so the dashboard / sessions tabs
    // don't accidentally inherit a landscape lock.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(readerOrientation, activity) {
        val previous = activity?.requestedOrientation
            ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.requestedOrientation = when (readerOrientation) {
            ReaderOrientation.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            ReaderOrientation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = previous
        }
    }

    // Pre-highlight the requested ayah when entering via the "search by
    // surah + ayah" flow. Runs exactly once because the keys are the
    // navigation arguments — they don't change during the lifetime of
    // a single MushafReaderScreen instance.
    LaunchedEffect(initialHighlightSurah, initialHighlightAyah) {
        if (initialHighlightSurah > 0 && initialHighlightAyah > 0) {
            readingViewModel.setInitialHighlight(
                page = initialPage,
                surah = initialHighlightSurah,
                ayah = initialHighlightAyah,
            )
            // Intentionally NOT calling translationViewModel.setAyahHighlight()
            // here — that side-effect pops the translation sheet open and
            // we want the search-jump to land on a clean reader view with
            // just the verse highlighted on the page.
        }
    }

    // Auto-start session if requested.
    //
    // We prefer the route-supplied overrides over the legacy DataStore
    // Flow because the latter races with the pager's first
    // `setCurrentPage(...)` emission. Without the overrides, opening
    // an existing multi-session would silently downgrade `targetPages`
    // to the user's `newSessionLimit` setting (typically 5), regardless
    // of what the session actually requested — the bug the user
    // reported as "target=10 but I'm limited to 6 pages forward".
    //
    // The keys include the overrides so navigating to a *different*
    // session (different start/target on the same composable
    // instance via `launchSingleTop`) re-arms with the new values.
    // `startSessionWithStart` itself is idempotent for unchanged args
    // so simple recompositions don't reset the timer.
    LaunchedEffect(
        startSessionAutomatically,
        sessionStartPageOverride,
        sessionTargetPagesOverride,
        initialPage,
    ) {
        if (!startSessionAutomatically) return@LaunchedEffect
        val resolvedStart = if (sessionStartPageOverride > 0) sessionStartPageOverride else initialPage
        val resolvedTarget = when {
            sessionTargetPagesOverride > 0 -> sessionTargetPagesOverride
            sessionTargetPages > 0 -> sessionTargetPages
            else -> newSessionLimit
        }
        readingViewModel.startSessionWithStart(
            startPage = resolvedStart,
            targetPages = resolvedTarget,
        )
    }

    // Auto-play audio when page changes (if already playing)
    LaunchedEffect(pagerState.currentPage, audioState) {
        if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) {
            audioViewModel.playPage(currentPageNumber)
        }
    }

    // Snackbar for error messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            readingViewModel.clearError()
        }
    }

    // Panels are visible iff the highlighted ayah is on the page
    // currently being viewed. Two safeguards in one expression:
    //  1. `highlightedAyah != null`     — user has actually selected
    //     a verse (we don't show panels for audio-driven highlights
    //     the user never asked for; that's reserved for the visual
    //     rectangle on the page only).
    //  2. `it.page == currentPageNumber` — the user has not swiped
    //     away from the page where the selection lives. Swiping back
    //     restores the panels because the highlight itself persists
    //     in the ViewModel; we just hide the chrome while it's
    //     out-of-frame so the chips never show stale page/juz info.
    val panelsVisible = highlightedAyah?.let { it.page == currentPageNumber } == true

    // System back button: when panels are showing, clear the highlight
    // first so the user gets a smooth dismiss step. A second back
    // press (now with no highlight) falls through to navigation.
    BackHandler(enabled = panelsVisible) {
        readingViewModel.clearHighlight()
    }

    // Detect landscape — the page image (1024×1656 px) is much
    // taller than the landscape viewport even at natural width,
    // so each page is wrapped in a vertical lazy container to let the
    // user scroll up/down through the page. We rely on the natural
    // aspect-ratio overflow rather than a `.scale()` modifier,
    // because `.scale()` is a visual-only transform that doesn't
    // extend the scrollable layout bounds.
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Cache the pager's nested-scroll connection so a fresh instance
    // isn't allocated on every recomposition. The connection is keyed
    // off the pager state — it stays valid for the lifetime of this
    // composable.
    val pageNestedScrollConnection = remember(pagerState) {
        androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection(
            pagerState,
            Orientation.Horizontal,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // HorizontalPager for page-to-page navigation in both
        // orientations (swipe left/right). In landscape the page
        // image overflows vertically (1024×1656 aspect ratio at
        // fillMaxWidth produces a height taller than the
        // viewport), so each page is wrapped in a vertical lazy
        // container to let the user scroll up/down through the page.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageNestedScrollConnection = pageNestedScrollConnection,
        ) { page ->
            // v3.0 / REQ-006: when audio is playing, override tap-highlight w/ audio-driven highlight.
            // Remembered to avoid allocating a new HighlightedAyah on every
            // recomposition (the audio state ticks frequently while playing).
            val audioDrivenHighlight = remember(
                page, audioState, currentAudioAyah?.surah, currentAudioAyah?.ayah,
            ) {
                currentAudioAyah?.let { ayahInfo ->
                    if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) {
                        com.quranreader.custom.data.model.HighlightedAyah(
                            page = page + 1,
                            surah = ayahInfo.surah,
                            ayah = ayahInfo.ayah,
                            isBookmarked = false,
                        )
                    } else null
                }
            }

            // In landscape: LazyColumn lets the
            // user scroll up/down through the page. We give the
            // renderer an EXPLICIT height (screenWidth × image
            // aspect ratio) so the page genuinely overflows the
            // viewport — relying on `aspectRatio` alone gets
            // constrained back to viewport height by intrinsic
            // measurements. In portrait: no scroll needed.
            if (isLandscape) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val pageHeightRatio = IMAGE_HEIGHT_PX.toFloat() / IMAGE_WIDTH_PX.toFloat()
                    val landscapePageHeight = (maxWidth * pageHeightRatio)
                        .coerceAtLeast(maxHeight + 1.dp)
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item(key = page) {
                            MushafImageRenderer(
                                pageNumber = page + 1,
                                highlightedAyah = audioDrivenHighlight ?: highlightedAyah,
                                onAyahLongPress = { s, a ->
                                    readingViewModel.onAyahTapped(page + 1, s, a)
                                    translationViewModel.setHighlightedAyah(s, a)
                                    translationViewModel.openPanel()
                                },
                                onAyahTap = { s, a ->
                                    readingViewModel.setInitialHighlight(page + 1, s, a)
                                },
                                onSingleTap = { readingViewModel.clearHighlight() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(landscapePageHeight),
                                fillContainer = true,
                            )
                        }
                    }
                }
            } else {
                MushafImageRenderer(
                    pageNumber = page + 1,
                    highlightedAyah = audioDrivenHighlight ?: highlightedAyah,
                    onAyahLongPress = { s, a ->
                        readingViewModel.onAyahTapped(page + 1, s, a)
                        translationViewModel.setHighlightedAyah(s, a)
                        translationViewModel.openPanel()
                    },
                    onAyahTap = { s, a ->
                        readingViewModel.setInitialHighlight(page + 1, s, a)
                    },
                    onSingleTap = { readingViewModel.clearHighlight() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── Slide-DOWN panel (top): verse context ─────────────────
        AnimatedVisibility(
            visible = panelsVisible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = Motion.emphasizedDecelerate(),
            ) + fadeIn(animationSpec = Motion.standard()),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = Motion.emphasizedAccelerate(),
            ) + fadeOut(animationSpec = Motion.short()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Panel data is sourced from the *highlight* itself, not
            // from the live pager. That keeps the chips consistent
            // during the exit animation when AnimatedVisibility is
            // still rendering the panel but the user has already
            // swiped to a different page.
            TopInfoPanel(
                pageNumber = highlightedAyah?.page ?: currentPageNumber,
                surahNumber = highlightedAyah?.surah ?: 1,
                ayahNumber = highlightedAyah?.ayah ?: 1,
                onClose = { readingViewModel.clearHighlight() },
                onNavigate = { showNavigateDialog = true },
            )
        }

        // ── Slide-UP panel (bottom): actions ──────────────────────
        AnimatedVisibility(
            visible = panelsVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = Motion.emphasizedDecelerate(),
            ) + fadeIn(animationSpec = Motion.standard()),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = Motion.emphasizedAccelerate(),
            ) + fadeOut(animationSpec = Motion.short()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            val isPlaying = audioState == com.quranreader.custom.data.audio.PlaybackState.Playing
            BottomActionPanel(
                isBookmarked = highlightedAyahBookmarked,
                isPlaying = isPlaying,
                isTranslateOpen = showTranslationPanel,
                isMemorizeOpen = showMemorizeOverlay,
                readerOrientation = readerOrientation,
                onBack = onBack,
                onBookmarkToggle = { readingViewModel.toggleAyahBookmark() },
                onTranslateToggle = {
                    val highlight = highlightedAyah
                    if (highlight != null) {
                        translationViewModel.setHighlightedAyah(highlight.surah, highlight.ayah)
                    }
                    translationViewModel.togglePanel()
                },
                onAudioToggle = {
                    if (isPlaying) audioViewModel.togglePlayPause()
                    else audioViewModel.playPage(currentPageNumber)
                },
                onMemorizeToggle = { showMemorizeOverlay = true },
                onOrientationCycle = { readingViewModel.cycleReaderOrientation() },
            )
        }

        // Translation panel — slides up from the bottom, sits over
        // the mushaf without dimming/blocking taps on the rest of
        // the page. Capped at 45% of the parent height inside the
        // composable itself.
        TranslationPanel(
            visible = showTranslationPanel,
            translations = translations,
            highlightedAyahNumber = translationHighlight,
            highlightedSurahNumber = highlightedAyah?.surah,
            scope = translationScope,
            isLoading = translationLoading,
            isEditionInstalled = isCurrentEditionInstalled,
            currentEdition = currentEdition,
            onScopeToggle = { translationViewModel.toggleScope() },
            onEditionsClick = {
                showEditionsDialog = true
                translationViewModel.refreshCatalogue()
            },
            onDismiss = { translationViewModel.closePanel() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        // Snackbar — anchored above the bottom panel so a transient
        // error never gets clipped under the action chrome. The 96 dp
        // bottom padding equals the panel's max height plus a margin.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (panelsVisible) 104.dp else 16.dp),
        )
    }

    // ── Session Complete Sheet ────────────────────────────────────
    if (showSessionCompleteSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                readingViewModel.closeSession()
                onBack()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = context.getString(R.string.session_complete_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                context.getString(
                                    R.string.session_complete_pages,
                                    pagesReadInSession,
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )

                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                context.getString(
                                    R.string.session_complete_time,
                                    readingViewModel.sessionDurationMinutes(),
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            readingViewModel.closeSession()
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(context.getString(R.string.common_close))
                    }

                    Button(
                        onClick = { readingViewModel.continueReadingSession() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(context.getString(R.string.session_continue_reading))
                    }
                }
            }
        }
    }

    // Editions catalogue dialog — opened from the chip in the
    // translation panel header.
    if (showEditionsDialog) {
        TranslationEditionsDialog(
            editions = editions,
            selectedEditionId = translationEditionId,
            downloadProgress = downloadProgressMap,
            isCatalogueRefreshing = catalogueRefreshing,
            onSelect = { edition ->
                translationViewModel.setEdition(edition.editionId)
                showEditionsDialog = false
            },
            onDownload = { edition -> translationViewModel.downloadEdition(edition) },
            onDelete = { editionId -> translationViewModel.deleteEdition(editionId) },
            onRefresh = { translationViewModel.refreshCatalogue() },
            onDismiss = { showEditionsDialog = false },
        )
    }

    // Unified navigate dialog — tabs for Surah+Ayah and Page.
    if (showNavigateDialog) {
        NavigateDialog(
            currentPage = currentPageNumber,
            onDismiss = { showNavigateDialog = false },
            onAyahResult = { page, surah, ayah ->
                showNavigateDialog = false
                readingViewModel.setInitialHighlight(page, surah, ayah)
                coroutineScope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, 603)) }
            },
            onPageResult = { page ->
                showNavigateDialog = false
                coroutineScope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, 603)) }
            },
        )
    }

    // v3.0 / REQ-008: Memorization (Hifz) overlay
    if (showMemorizeOverlay) {
        val firstAyahOnPage = remember(currentPageNumber) {
            com.quranreader.custom.data.audio.AudioUrlResolver
                .getAyahsForPage(currentPageNumber)
                .firstOrNull()
        }
        val initialAyah = currentAudioAyah?.let {
            com.quranreader.custom.data.audio.sync.AyahKey(it.surah, it.ayah)
        } ?: highlightedAyah?.let {
            com.quranreader.custom.data.audio.sync.AyahKey(it.surah, it.ayah)
        } ?: firstAyahOnPage?.let {
            com.quranreader.custom.data.audio.sync.AyahKey(it.first, it.second)
        } ?: com.quranreader.custom.data.audio.sync.AyahKey(1, 1)

        com.quranreader.custom.ui.screens.memorization.MemorizationOverlay(
            initialAyah = initialAyah,
            onDismiss = { showMemorizeOverlay = false },
        )
    }
}

// ── Slide-down panel (top) ──────────────────────────────────────────────────

/**
 * Read-only context strip anchored to the top of the screen. Shows the
 * surah, the highlighted verse, the page number, and the juz. DPI-safe
 * symmetrical layout: close on the left, surah info centred, chips on
 * the right. A single **Navigate** button opens the unified jump
 * dialog (Surah+Ayah *or* Page tabs).
 *
 * Layout:
 *  - 280 ≤ width ≤ 480 dp, fills available width within those bounds.
 *  - 48 dp close button (Material a11y minimum).
 *  - Surah / ayah label takes a flexible weight so long surah names
 *    truncate with ellipsis instead of pushing the chips off-screen.
 *  - Page + Juz chips sit on the trailing edge; on compact screens
 *    (< 360 dp) they wrap below the surah label and the Navigate
 *    button sits centred at full width.
 */
@Composable
private fun TopInfoPanel(
    pageNumber: Int,
    surahNumber: Int,
    ayahNumber: Int,
    onClose: () -> Unit,
    onNavigate: () -> Unit,
) {
    val englishName = remember(surahNumber) { QuranInfo.getSurahEnglishName(surahNumber) }
    val juz = remember(pageNumber) { QuranInfo.getJuzForPage(pageNumber) }

    PanelSurface(
        modifier = Modifier.fillMaxWidth(),
    ) { compact ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Row 1: Close | Surah info | Chips ──────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.mushaf_panel_close),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                }

                Spacer(Modifier.size(8.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = englishName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.mushaf_ayah_label,
                            surahNumber,
                            ayahNumber,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!compact) {
                    Spacer(Modifier.size(8.dp))
                    InfoChipRow(pageNumber = pageNumber, juz = juz)
                }
            }

            // Compact screens: chips on a second row
            if (compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoChipRow(pageNumber = pageNumber, juz = juz)
                }
            }

            // ── Row 2: Single Navigate button (centred) ────────────
            Surface(
                onClick = onNavigate,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.mushaf_action_navigate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Two side-by-side context chips: page number and juz. Pulled out so
 * both the wide-and-narrow layouts in [TopInfoPanel] share a single
 * source of truth for the chips' look.
 */
@Composable
private fun InfoChipRow(
    pageNumber: Int,
    juz: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ContextChip(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = stringResource(R.string.mushaf_page_label, pageNumber),
        )
        ContextChip(
            icon = Icons.Default.Bookmark,
            label = stringResource(R.string.mushaf_juz_label, juz),
        )
    }
}

@Composable
private fun ContextChip(
    icon: ImageVector,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Slide-up panel (bottom) ─────────────────────────────────────────────────

/**
 * Action strip anchored to the bottom of the screen. The bookmark
 * icon binds to the *highlighted ayah*, not the page — toggling 2:255
 * does not affect 2:256 even when both are bookmarked on the same
 * page. The audio button still drives page-level playback (matches
 * the existing AudioService API), but the rest of the controls
 * operate on the selected verse.
 *
 * Six evenly-spaced 48 dp icon buttons fit comfortably on the
 * narrowest supported device (5" / 360 dp); the row is wrapped in
 * `horizontalScroll` as a safety net for very small screens.
 *
 * The orientation toggle cycles AUTO → PORTRAIT → LANDSCAPE so the
 * user can pin the reader into landscape (zoomed mushaf view) without
 * flipping the system's global rotation lock.
 */
@Composable
private fun BottomActionPanel(
    isBookmarked: Boolean,
    isPlaying: Boolean,
    isTranslateOpen: Boolean,
    isMemorizeOpen: Boolean,
    readerOrientation: ReaderOrientation,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTranslateToggle: () -> Unit,
    onAudioToggle: () -> Unit,
    onMemorizeToggle: () -> Unit,
    onOrientationCycle: () -> Unit,
) {
    PanelSurface(
        modifier = Modifier.fillMaxWidth(),
    ) { compact ->
        // 6 × 48dp icon buttons + spacing comfortably fits a 360dp
        // screen but starts to crowd on `sw320dp` devices (e.g. small
        // pre-Pixel handsets, foldable inner panels). Wrapping the
        // row in `horizontalScroll` on the compact breakpoint lets
        // the user pan past any clipped chrome instead of having
        // touches eat each other; on standard-size phones the
        // arrangement stays `SpaceEvenly` and never triggers the
        // scroll because the row already fits.
        val rowModifier = if (compact) {
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        }
        Row(
            modifier = rowModifier,
            horizontalArrangement = if (compact) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.mushaf_action_back),
                active = false,
                onClick = onBack,
            )
            PanelIconButton(
                icon = Icons.Default.Translate,
                contentDescription = stringResource(R.string.mushaf_action_translate),
                active = isTranslateOpen,
                onClick = onTranslateToggle,
            )
            PanelIconButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.mushaf_action_audio_pause
                    else R.string.mushaf_action_audio_play,
                ),
                active = isPlaying,
                onClick = onAudioToggle,
            )
            PanelIconButton(
                icon = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.mushaf_action_memorize),
                active = isMemorizeOpen,
                onClick = onMemorizeToggle,
            )
            OrientationToggleButton(
                orientation = readerOrientation,
                onClick = onOrientationCycle,
            )
            BookmarkIconButton(
                isBookmarked = isBookmarked,
                onClick = onBookmarkToggle,
            )
        }
    }
}

/**
 * Cycles AUTO → PORTRAIT → LANDSCAPE. Icon swaps to communicate the
 * *current* state, not the *next* — users tend to read the icon as
 * "what am I locked into right now".
 */
@Composable
private fun OrientationToggleButton(
    orientation: ReaderOrientation,
    onClick: () -> Unit,
) {
    val (icon, description) = when (orientation) {
        ReaderOrientation.AUTO -> Icons.Default.ScreenRotation to "Orientation: Auto"
        ReaderOrientation.PORTRAIT -> Icons.Default.ScreenLockPortrait to "Orientation: Portrait"
        ReaderOrientation.LANDSCAPE -> Icons.Default.ScreenLockLandscape to "Orientation: Landscape"
    }
    PanelIconButton(
        icon = icon,
        contentDescription = description,
        active = orientation != ReaderOrientation.AUTO,
        onClick = onClick,
    )
}

// ── Shared panel chrome ─────────────────────────────────────────────────────

/**
 * Translucent surface used by both reader panels. Replaces the
 * previous Haze-backed frosted-glass effect with a regular Material 3
 * surface tinted at 92% alpha — looks closer to a native Android panel
 * and renders identically on every API level (no Android 12+
 * `RenderEffect` requirement).
 *
 * Wraps a `BoxWithConstraints` so child content can adapt to a
 * `compact` (< 360 dp) breakpoint without a second measure pass. The
 * width follows the shared [responsivePanelMaxWidth] tier table so
 * phones and tablets all stay visually consistent — the panel never
 * fills the full width of a 10" tablet (which would dwarf the
 * mushaf), and on compact handsets it still spans 92% of the screen.
 * Height grows with content; min-height is enforced by the children
 * (touch targets are 48 dp). The inner Surface is centred horizontally
 * so the panel hovers in the middle of wider screens instead of
 * left-anchoring against the start edge.
 */
@Composable
private fun PanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable (compact: Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val available = maxWidth
        val maxPanel = com.quranreader.custom.ui.components.responsivePanelMaxWidth(available)
        val compact = available < 360.dp

        val panelShape = RoundedCornerShape(24.dp)
        Surface(
            shape = panelShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(
                    min = com.quranreader.custom.ui.components.MIN_PANEL_WIDTH.coerceAtMost(available),
                    max = maxPanel,
                )
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = panelShape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clip(panelShape)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    shape = panelShape,
                ),
        ) {
            content(compact)
        }
    }
}

/**
 * Square IconButton tuned for the reader panels: 48 dp touch target
 * (Material a11y minimum) with a 22 dp glyph. Tints flip between
 * `onSurface @ 85%` and `primary` based on the [active] flag, which
 * the panels use to communicate "translation sheet open", "audio
 * playing", etc.
 */
@Composable
private fun PanelIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Bookmark variant of [PanelIconButton]. Has its own composable
 * because the icon swaps (Bookmark ↔ BookmarkBorder) and gets a
 * scale-on-press animation we don't want firing on the other
 * controls — it's the panel's most "celebratory" interaction so it
 * earns the micro-bounce.
 */
@Composable
private fun BookmarkIconButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.85f
            isBookmarked -> 1.1f
            else -> 1.0f
        },
        animationSpec = Motion.short(),
        label = "Bookmark.scale",
    )
    val tint = if (isBookmarked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val description = stringResource(
        if (isBookmarked) R.string.mushaf_action_bookmark_remove
        else R.string.mushaf_action_bookmark_add,
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(scale),
        )
    }
}

// ── Unified Navigate Dialog ────────────────────────────────────────────────

/**
 * Single dialog with two tabs: **Surah & Ayah** (opens the full
 * [AyahSearchDialog] inline) and **Page** (numeric 1–604 input).
 *
 * DPI-adaptive: width = min(92% of window, 480 dp) ≥ 280 dp;
 * height = min(85% of window, 640 dp). Renders identically from
 * 5" phones to 12" tablets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigateDialog(
    currentPage: Int,
    onDismiss: () -> Unit,
    onAyahResult: (page: Int, surah: Int, ayah: Int) -> Unit,
    onPageResult: (page: Int) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showAyahSearchDialog by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val dialogMaxWidth = com.quranreader.custom.ui.components.responsivePanelMaxWidth(maxWidth)

            Surface(
                modifier = Modifier
                    .widthIn(
                        min = com.quranreader.custom.ui.components.MIN_PANEL_WIDTH.coerceAtMost(maxWidth),
                        max = dialogMaxWidth,
                    )
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column {
                    // ── Title + close ─────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.mushaf_navigate_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.mushaf_panel_close),
                            )
                        }
                    }

                    // ── Tab row ───────────────────────────────────
                    val tabTitles = listOf(
                        stringResource(R.string.mushaf_navigate_tab_ayah),
                        stringResource(R.string.mushaf_navigate_tab_page),
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold
                                            else FontWeight.Medium,
                                    )
                                },
                            )
                        }
                    }

                    // ── Tab content ───────────────────────────────
                    when (selectedTab) {
                        0 -> {
                            // Surah & Ayah tab — opens AyahSearchDialog
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                )
                                Text(
                                    text = stringResource(R.string.search_ayah_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { showAyahSearchDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.search_ayah_title))
                                }
                            }
                        }
                        1 -> {
                            // Page tab — simple numeric input
                            var pageInput by rememberSaveable {
                                mutableStateOf(currentPage.toString())
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                OutlinedTextField(
                                    value = pageInput,
                                    onValueChange = { value ->
                                        pageInput = value.filter { it.isDigit() }.take(3)
                                    },
                                    singleLine = true,
                                    label = {
                                        Text(stringResource(R.string.mushaf_go_to_page_hint))
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = {
                                        val target = pageInput.toIntOrNull()?.coerceIn(1, 604)
                                        if (target != null) onPageResult(target)
                                    },
                                    enabled = pageInput.toIntOrNull()?.let { it in 1..604 } == true,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.mushaf_go_to_page_go))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Surah & Ayah picker — opens on top of the navigate dialog
    if (showAyahSearchDialog) {
        AyahSearchDialog(
            onDismiss = { showAyahSearchDialog = false },
            onResult = { page, surah, ayah ->
                showAyahSearchDialog = false
                onAyahResult(page, surah, ayah)
            },
        )
    }
}
