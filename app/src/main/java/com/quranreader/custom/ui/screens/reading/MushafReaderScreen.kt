package com.quranreader.custom.ui.screens.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.R
import com.quranreader.custom.ui.components.AudioControlBar
import com.quranreader.custom.ui.components.TranslationBottomSheet
import com.quranreader.custom.ui.components.mushaf.MushafImageRenderer
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.TranslationViewModel
import kotlinx.coroutines.launch

/**
 * Mushaf Reader Screen - Layer 2
 * Full screen Quran page display with audio playback and translation overlay
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MushafReaderScreen(
    initialPage: Int,
    onBack: () -> Unit = {},
    startSessionAutomatically: Boolean = false,
    readingViewModel: ReadingViewModel = hiltViewModel(),
    audioViewModel: AudioViewModel = hiltViewModel(),
    translationViewModel: TranslationViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, 603),
        pageCount = { 604 }
    )
    val scope = rememberCoroutineScope()

    val isBookmarked by readingViewModel.isBookmarked.collectAsState()
    val highlightedAyah by readingViewModel.highlightedAyah.collectAsState()
    val ayahCoordinates by readingViewModel.ayahCoordinates.collectAsState()
    val showAyahDialog by readingViewModel.showAyahDialog.collectAsState()
    val errorMessage by readingViewModel.errorMessage.collectAsState()
    val showSessionCompleteSheet by readingViewModel.showSessionCompleteSheet.collectAsState()
    val pagesReadInSession by readingViewModel.pagesReadInSession.collectAsState()
    val sessionState by readingViewModel.sessionState.collectAsState()
    val sessionTargetPages by readingViewModel.sessionTargetPages.collectAsState()
    val newSessionLimit by readingViewModel.newSessionLimit.collectAsState()

    // Audio state
    val audioState by audioViewModel.playbackState.collectAsState()
    val currentAudioAyah by audioViewModel.currentAyah.collectAsState()
    val isBuffering by audioViewModel.isBuffering.collectAsState()

    // Translation state
    val showTranslationSheet by translationViewModel.showTranslationSheet.collectAsState()
    val translations by translationViewModel.translations.collectAsState()
    val translationLoading by translationViewModel.isLoading.collectAsState()
    val translationLang by translationViewModel.translationLanguage.collectAsState()
    val translationHighlight by translationViewModel.highlightedAyahNumber.collectAsState()

    val currentPageNumber = pagerState.currentPage + 1
    
    // State for info panel visibility
    var showInfoPanel by remember { mutableStateOf(false) }
    // v3.0: state for memorization overlay
    var showMemorizeOverlay by remember { mutableStateOf(false) }
    
    // Auto-hide panel after 3 seconds
    LaunchedEffect(showInfoPanel) {
        if (showInfoPanel) {
            kotlinx.coroutines.delay(3000)
            showInfoPanel = false
        }
    }

    // Update current page when pager changes
    LaunchedEffect(pagerState.currentPage) {
        readingViewModel.setCurrentPage(pagerState.currentPage + 1)
        translationViewModel.loadTranslationsForPage(pagerState.currentPage + 1)
    }

    // Auto-start session if requested
    LaunchedEffect(startSessionAutomatically, sessionTargetPages, sessionState, currentPageNumber) {
        if (startSessionAutomatically && sessionState == com.quranreader.custom.ui.viewmodel.SessionState.IDLE) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Mushaf pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                // v3.0 / REQ-006: when audio is playing, override tap-highlight w/ audio-driven highlight
                val audioDrivenHighlight = currentAudioAyah?.let { ayahInfo ->
                    if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) {
                        com.quranreader.custom.data.model.HighlightedAyah(
                            page = page + 1,
                            surah = ayahInfo.surah,
                            ayah = ayahInfo.ayah,
                            isBookmarked = false
                        )
                    } else null
                }
                // v5 mushaf image renderer — bundled transparent-bg WebP
                // pages tinted with MaterialTheme.colorScheme.onSurface
                // so light/dark themes both render correctly with no
                // CDN download. Tap detection is driven by the bundled
                // ayahinfo.db (per-glyph pixel coordinates).
                MushafImageRenderer(
                    pageNumber = page + 1,
                    highlightedAyah = audioDrivenHighlight ?: highlightedAyah,
                    onAyahLongPress = { s, a ->
                        readingViewModel.onAyahTapped(page + 1, s, a)
                        translationViewModel.setAyahHighlight(a)
                    },
                    onSingleTap = { showInfoPanel = !showInfoPanel },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Compact Slide-down Info Panel
        androidx.compose.animation.AnimatedVisibility(
            visible = showInfoPanel,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { -it }
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { -it }
            ) + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp) // Extra padding to avoid status bar
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top row: Back, Page info, Bookmark, Translation toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { translationViewModel.toggleTranslationSheet() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = "Translation",
                                    tint = if (showTranslationSheet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // v3.0 / REQ-008: Memorize entry — opens hifz overlay for current ayah
                            IconButton(
                                onClick = { showMemorizeOverlay = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Memorize",
                                    tint = if (showMemorizeOverlay) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            context.getString(R.string.mushaf_page_info, currentPageNumber, 604),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) {
                                        audioViewModel.togglePlayPause()
                                    } else {
                                        audioViewModel.playPage(currentPageNumber)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) Icons.Default.Pause
                                    else Icons.Default.PlayArrow,
                                    contentDescription = "Audio",
                                    tint = if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // v3.0 / REQ-015: scale-on-tap micro-interaction + animated fill
                            val bookmarkInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val bookmarkPressed by bookmarkInteractionSource.collectIsPressedAsState()
                            val bookmarkScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (bookmarkPressed) 0.85f else if (isBookmarked) 1.1f else 1.0f,
                                animationSpec = com.quranreader.custom.ui.theme.Motion.short(),
                                label = "Bookmark.scale"
                            )
                            IconButton(
                                onClick = { readingViewModel.toggleBookmark() },
                                modifier = Modifier.size(36.dp),
                                interactionSource = bookmarkInteractionSource
                            ) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(bookmarkScale)
                                )
                            }
                        }
                    }
                    
                    Divider(thickness = 0.5.dp)
                    
                    // Bottom row: Surah and Juz info (compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Surah info
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                com.quranreader.custom.data.QuranInfo.getSurahNameForPage(currentPageNumber),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                        
                        Text(
                            "|",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        
                        // Juz info
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Juz ${com.quranreader.custom.data.QuranInfo.getJuzForPage(currentPageNumber)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // Floating toggle button removed - tap anywhere on page to toggle info panel

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // Session Complete Sheet
    if (showSessionCompleteSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                readingViewModel.closeSession()
                onBack()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        context.getString(R.string.session_complete_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(context.getString(R.string.session_complete_pages, pagesReadInSession)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    AssistChip(
                        onClick = {},
                        label = { Text(context.getString(R.string.session_complete_time, readingViewModel.sessionDurationMinutes())) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            readingViewModel.closeSession()
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.common_close))
                    }

                    Button(
                        onClick = { readingViewModel.continueReadingSession() },
                        modifier = Modifier.weight(1f)
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
            onDismiss = { translationViewModel.toggleTranslationSheet() }
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
            onDismiss = { showMemorizeOverlay = false }
        )
    }
}
