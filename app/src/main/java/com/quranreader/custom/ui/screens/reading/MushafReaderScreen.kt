package com.quranreader.custom.ui.screens.reading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.ui.components.TranslationBottomSheet
import com.quranreader.custom.ui.components.mushaf.MushafImageRenderer
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.TranslationViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

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
    val sessionState by readingViewModel.sessionState.collectAsStateWithLifecycle()
    val sessionTargetPages by readingViewModel.sessionTargetPages.collectAsStateWithLifecycle()
    val newSessionLimit by readingViewModel.newSessionLimit.collectAsStateWithLifecycle()

    // Audio state
    val audioState by audioViewModel.playbackState.collectAsStateWithLifecycle()
    val currentAudioAyah by audioViewModel.currentAyah.collectAsStateWithLifecycle()

    // Translation state
    val showTranslationSheet by
        translationViewModel.showTranslationSheet.collectAsStateWithLifecycle()
    val translations by translationViewModel.translations.collectAsStateWithLifecycle()
    val translationLoading by translationViewModel.isLoading.collectAsStateWithLifecycle()
    val translationLang by translationViewModel.translationLanguage.collectAsStateWithLifecycle()
    val translationHighlight by
        translationViewModel.highlightedAyahNumber.collectAsStateWithLifecycle()

    val currentPageNumber = pagerState.currentPage + 1

    // v3.0: state for memorization overlay
    var showMemorizeOverlay by remember { mutableStateOf(false) }

    // Update current page when pager changes
    LaunchedEffect(pagerState.currentPage) {
        readingViewModel.setCurrentPage(pagerState.currentPage + 1)
        translationViewModel.loadTranslationsForPage(pagerState.currentPage + 1)
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

    // Auto-start session if requested
    LaunchedEffect(startSessionAutomatically, sessionTargetPages, sessionState, currentPageNumber) {
        if (startSessionAutomatically &&
            sessionState == com.quranreader.custom.ui.viewmodel.SessionState.IDLE
        ) {
            val targetPages = if (sessionTargetPages > 0) sessionTargetPages else newSessionLimit
            readingViewModel.startNewSession(targetPages)
        }
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

    // Panels are visible iff an ayah is currently highlighted. This
    // flips the previous "tap-toggle" model: now the *highlight* IS
    // the panel state. Audio-driven highlights also count, so the
    // panels follow along while the page is being recited.
    val panelsVisible = highlightedAyah != null

    // System back button: when panels are showing, clear the highlight
    // first so the user gets a smooth dismiss step. A second back
    // press (now with no highlight) falls through to navigation.
    BackHandler(enabled = panelsVisible) {
        readingViewModel.clearHighlight()
    }

    // Haze captures pixels rendered *behind* it and exposes them to
    // hazeChild() callers as a blurred backdrop. Both panels register
    // as separate hazeChildren on the same source so the calligraphy
    // outside their bounds remains sharp and tappable for the next
    // ayah selection.
    val hazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mushaf pages — registered as the haze source. The pager
        // itself is NOT blurred; only the area inside each panel's
        // shape is sampled and blurred by hazeChild().
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState),
        ) { page ->
            // v3.0 / REQ-006: when audio is playing, override tap-highlight w/ audio-driven highlight
            val audioDrivenHighlight = currentAudioAyah?.let { ayahInfo ->
                if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) {
                    com.quranreader.custom.data.model.HighlightedAyah(
                        page = page + 1,
                        surah = ayahInfo.surah,
                        ayah = ayahInfo.ayah,
                        isBookmarked = false,
                    )
                } else null
            }
            // v5 mushaf image renderer — bundled transparent-bg WebP
            // pages tinted with MaterialTheme.colorScheme.onSurface so
            // light/dark themes both render correctly with no CDN
            // download. Tap detection is driven by the bundled
            // ayahinfo.db (per-glyph pixel coordinates).
            //
            // Gesture model:
            //  - Tap on a glyph → set highlight (panels appear).
            //  - Tap on margin / negative space → clear highlight
            //    (panels disappear). This is the cleanest way to
            //    dismiss without drilling into a separate "close"
            //    affordance on the page itself.
            //  - Long-press on a glyph → open ayah action dialog +
            //    translation sheet (existing flow).
            MushafImageRenderer(
                pageNumber = page + 1,
                highlightedAyah = audioDrivenHighlight ?: highlightedAyah,
                onAyahLongPress = { s, a ->
                    readingViewModel.onAyahTapped(page + 1, s, a)
                    translationViewModel.setAyahHighlight(a)
                },
                onAyahTap = { s, a ->
                    readingViewModel.setInitialHighlight(page + 1, s, a)
                },
                onSingleTap = { readingViewModel.clearHighlight() },
                modifier = Modifier.fillMaxSize(),
            )
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
            TopInfoPanel(
                hazeState = hazeState,
                pageNumber = currentPageNumber,
                surahNumber = highlightedAyah?.surah ?: 1,
                ayahNumber = highlightedAyah?.ayah ?: 1,
                onClose = { readingViewModel.clearHighlight() },
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
                hazeState = hazeState,
                isBookmarked = highlightedAyahBookmarked,
                isPlaying = isPlaying,
                isTranslateOpen = showTranslationSheet,
                isMemorizeOpen = showMemorizeOverlay,
                onBack = onBack,
                onBookmarkToggle = { readingViewModel.toggleAyahBookmark() },
                onTranslateToggle = {
                    val ayah = highlightedAyah?.ayah
                    if (ayah != null) translationViewModel.setAyahHighlight(ayah)
                    translationViewModel.toggleTranslationSheet()
                },
                onAudioToggle = {
                    if (isPlaying) audioViewModel.togglePlayPause()
                    else audioViewModel.playPage(currentPageNumber)
                },
                onMemorizeToggle = { showMemorizeOverlay = true },
            )
        }

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

    // Translation Bottom Sheet
    if (showTranslationSheet) {
        TranslationBottomSheet(
            translations = translations,
            highlightedAyahNumber = translationHighlight,
            isLoading = translationLoading,
            currentLanguage = translationLang,
            onDismiss = { translationViewModel.toggleTranslationSheet() },
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
 * surah, the highlighted verse, the page number, and the juz. Single
 * close button on the leading edge clears the highlight (which also
 * dismisses the slide-up panel).
 *
 * Layout:
 *  - 280 ≤ width ≤ 480 dp, fills available width within those bounds.
 *  - 48 dp close button (Material a11y minimum).
 *  - Surah / ayah label takes a flexible weight so long surah names
 *    truncate with ellipsis instead of pushing the chips off-screen.
 *  - Page + Juz chips sit on the trailing edge in a single row; they
 *    wrap onto a second row only on the narrowest devices via the
 *    `breakpoint` flag computed from `BoxWithConstraints`.
 */
@Composable
private fun TopInfoPanel(
    hazeState: HazeState,
    pageNumber: Int,
    surahNumber: Int,
    ayahNumber: Int,
    onClose: () -> Unit,
) {
    val englishName = remember(surahNumber) { QuranInfo.getSurahEnglishName(surahNumber) }
    val juz = remember(pageNumber) { QuranInfo.getJuzForPage(pageNumber) }

    PanelSurface(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
    ) { compact ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 0.dp),
        ) {
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
                    InfoChipRow(
                        pageNumber = pageNumber,
                        juz = juz,
                    )
                }
            }

            // Narrow phones (< 360 dp) — the chips wrap onto a second
            // line so the surah name still has room to breathe. We
            // pre-decide via the BoxWithConstraints in PanelSurface so
            // there's no measure pass thrash.
            if (compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp), // align under the title (close button + spacer)
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoChipRow(
                        pageNumber = pageNumber,
                        juz = juz,
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
 * Five evenly-spaced 48 dp icon buttons fit comfortably on the
 * narrowest supported device (5" / 360 dp) and centre-justify on
 * tablets via `widthIn(max = 480.dp)`.
 */
@Composable
private fun BottomActionPanel(
    hazeState: HazeState,
    isBookmarked: Boolean,
    isPlaying: Boolean,
    isTranslateOpen: Boolean,
    isMemorizeOpen: Boolean,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTranslateToggle: () -> Unit,
    onAudioToggle: () -> Unit,
    onMemorizeToggle: () -> Unit,
) {
    PanelSurface(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
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
            BookmarkIconButton(
                isBookmarked = isBookmarked,
                onClick = onBookmarkToggle,
            )
        }
    }
}

// ── Shared panel chrome ─────────────────────────────────────────────────────

/**
 * Frosted-glass surface used by both panels. Wraps a `BoxWithConstraints`
 * so child content can adapt to a `compact` (< 360 dp) breakpoint
 * without a second measure pass. Width is clamped to the same band
 * the search dialog uses (`280..480 dp`) so chrome looks consistent
 * across the app.
 *
 * Height grows with content; min-height is enforced by the children
 * (touch targets are 48 dp). The 24 dp corner radius matches the
 * Material 3 expressive shape token used elsewhere in the reader.
 */
@Composable
private fun PanelSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable (compact: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        // Cap the panel at min(92% of available width, 480 dp) and
        // floor it at 280 dp. We read from BoxWithConstraints' scope —
        // not LocalConfiguration — so the math is driven by the *real*
        // parent constraint after status/navigation bars and the
        // host's horizontal padding, which is what we actually want
        // for sizing on foldables and split-screen layouts.
        val available = maxWidth
        val maxPanel = (available * 0.92f).coerceAtMost(480.dp)
        val compact = available < 360.dp

        val panelShape = RoundedCornerShape(24.dp)
        val panelTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        Box(
            modifier = Modifier
                .widthIn(min = 280.dp.coerceAtMost(available), max = maxPanel)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = panelShape,
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                )
                .clip(panelShape)
                .hazeChild(
                    state = hazeState,
                    shape = panelShape,
                    style = HazeStyle(
                        tint = panelTint,
                        blurRadius = 50.dp,
                        noiseFactor = 0.04f,
                    ),
                )
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.05f),
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

