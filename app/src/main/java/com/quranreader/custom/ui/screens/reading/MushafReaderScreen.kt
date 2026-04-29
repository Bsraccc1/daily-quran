package com.quranreader.custom.ui.screens.reading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import kotlinx.coroutines.launch
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase.Companion.IMAGE_HEIGHT_PX
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase.Companion.IMAGE_WIDTH_PX
import com.quranreader.custom.ui.viewmodel.AutoSaveTick
import com.quranreader.custom.data.preferences.ReaderOrientation
import com.quranreader.custom.ui.components.TranslationEditionsDialog
import com.quranreader.custom.ui.components.TranslationPanel
import com.quranreader.custom.ui.components.mushaf.MushafImageRenderer
import com.quranreader.custom.ui.screens.search.AyahSearchDialog
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.TranslationViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MushafReaderScreen(
    initialPage: Int,
    onBack: () -> Unit = {},
    startSessionAutomatically: Boolean = false,
    sessionStartPageOverride: Int = 0,
    sessionTargetPagesOverride: Int = 0,
    initialHighlightSurah: Int = 0,
    initialHighlightAyah: Int = 0,
    readingViewModel: ReadingViewModel = hiltViewModel(),
    audioViewModel: AudioViewModel = hiltViewModel(),
    translationViewModel: TranslationViewModel = hiltViewModel(),
) {
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
    val autoSaveTick by readingViewModel.autoSaveTick.collectAsStateWithLifecycle()

    val audioState by audioViewModel.playbackState.collectAsStateWithLifecycle()
    val currentAudioAyah by audioViewModel.currentAyah.collectAsStateWithLifecycle()

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

    var showMemorizeOverlay by remember { mutableStateOf(false) }

    var showEditionsDialog by remember { mutableStateOf(false) }

    var showNavigateDialog by remember { mutableStateOf(false) }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        readingViewModel.setCurrentPage(pagerState.currentPage + 1)
        translationViewModel.setCurrentPage(pagerState.currentPage + 1)
    }

    LaunchedEffect(highlightedAyah?.surah, highlightedAyah?.ayah) {
        translationViewModel.setHighlightedAyah(
            highlightedAyah?.surah,
            highlightedAyah?.ayah,
        )
    }

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

    LaunchedEffect(initialHighlightSurah, initialHighlightAyah) {
        if (initialHighlightSurah > 0 && initialHighlightAyah > 0) {
            readingViewModel.setInitialHighlight(
                page = initialPage,
                surah = initialHighlightSurah,
                ayah = initialHighlightAyah,
            )
        }
    }

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

    LaunchedEffect(pagerState.currentPage, audioState) {
        if (audioState == com.quranreader.custom.data.audio.PlaybackState.Playing) {
            audioViewModel.playPage(currentPageNumber)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            readingViewModel.clearError()
        }
    }

    val panelsVisible = highlightedAyah?.let { it.page == currentPageNumber } == true

    BackHandler(enabled = panelsVisible) {
        readingViewModel.clearHighlight()
    }

    var landscapeZoom by rememberSaveable { mutableFloatStateOf(1f) }

    val pageNestedScrollConnection = remember(pagerState) {
        androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection(
            pagerState,
            Orientation.Horizontal,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(if (showSessionCompleteSheet) Modifier.blur(20.dp) else Modifier),
    ) {
        val isLandscape = maxWidth > maxHeight
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageNestedScrollConnection = pageNestedScrollConnection,
        ) { page ->
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

            if (isLandscape) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val pageHeightRatio = IMAGE_HEIGHT_PX.toFloat() / IMAGE_WIDTH_PX.toFloat()
                    val basePageHeight = (maxWidth * pageHeightRatio)
                        .coerceAtLeast(maxHeight + 1.dp)

                    val zoomedWidth = maxWidth * landscapeZoom
                    val zoomedHeight = basePageHeight * landscapeZoom

                    val listState = rememberLazyListState()
                    val hScrollState = androidx.compose.foundation.rememberScrollState()
                    val outerModifier = if (landscapeZoom > 1f) {
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScrollState)
                    } else {
                        Modifier.fillMaxSize()
                    }
                    Box(modifier = outerModifier) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(zoomedWidth)
                                .fillMaxHeight(),
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
                                        .height(zoomedHeight),
                                    fillContainer = true,
                                )
                            }
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
                pageNumber = highlightedAyah?.page ?: currentPageNumber,
                surahNumber = highlightedAyah?.surah ?: 1,
                ayahNumber = highlightedAyah?.ayah ?: 1,
                autoSaveTick = autoSaveTick,
                onClose = { readingViewModel.clearHighlight() },
                onNavigate = { showNavigateDialog = true },
            )
        }

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
                isLandscape = isLandscape,
                landscapeZoom = landscapeZoom,
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
                onZoomIn = { landscapeZoom = (landscapeZoom + 0.25f).coerceAtMost(3f) },
                onZoomOut = { landscapeZoom = (landscapeZoom - 0.25f).coerceAtLeast(0.5f) },
                onZoomReset = { landscapeZoom = 1f },
            )
        }

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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (panelsVisible) 104.dp else 16.dp),
        )

        AnimatedVisibility(
            visible = autoSaveTick !is AutoSaveTick.Idle,
            enter = fadeIn(animationSpec = Motion.standard()) +
                slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = Motion.emphasizedDecelerate(),
                ),
            exit = fadeOut(animationSpec = Motion.short()) +
                slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = Motion.emphasizedAccelerate(),
                ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.End,
                    ),
                )
                .padding(top = 4.dp, end = 16.dp),
        ) {
            FloatingAutoSaveIndicator(state = autoSaveTick)
        }
    }

    if (showSessionCompleteSheet) {
        SessionCompleteSplash(
            pagesRead = pagesReadInSession,
            durationMinutes = readingViewModel.sessionDurationMinutes(),
            onContinue = { readingViewModel.continueReadingSession() },
            onClose = {
                readingViewModel.closeSession()
                onBack()
            },
        )
    }

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

@Composable
private fun TopInfoPanel(
    pageNumber: Int,
    surahNumber: Int,
    ayahNumber: Int,
    autoSaveTick: AutoSaveTick = AutoSaveTick.Idle,
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
                        autoSaveTick = autoSaveTick,
                    )
                }
            }

            if (compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoChipRow(
                        pageNumber = pageNumber,
                        juz = juz,
                        autoSaveTick = autoSaveTick,
                    )
                }
            }

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

@Composable
private fun InfoChipRow(
    pageNumber: Int,
    juz: Int,
    autoSaveTick: AutoSaveTick,
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
        AutoSaveChip(state = autoSaveTick)
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

@Composable
private fun FloatingAutoSaveIndicator(state: AutoSaveTick) {
    val isCounting = state is AutoSaveTick.Counting
    val icon = if (isCounting) Icons.Default.Save else Icons.Default.CheckCircle
    val label: String? = when (state) {
        is AutoSaveTick.Counting -> stringResource(
            if (state.byPages) R.string.mushaf_autosave_counting_pages
            else R.string.mushaf_autosave_counting,
            state.secondsLeft,
        )
        AutoSaveTick.Saved -> stringResource(R.string.mushaf_autosave_saved)
        AutoSaveTick.Idle -> null
    }
    val accent = if (isCounting) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.widthIn(max = 160.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state is AutoSaveTick.Counting) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(14.dp),
                        color = accent,
                        strokeWidth = 1.5.dp,
                        trackColor = accent.copy(alpha = 0.25f),
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(
                        R.string.mushaf_autosave_content_description,
                    ),
                    modifier = Modifier.size(if (isCounting) 9.dp else 14.dp),
                    tint = accent,
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AutoSaveChip(state: AutoSaveTick) {
    val isCounting = state is AutoSaveTick.Counting
    val icon = if (isCounting) Icons.Default.Save else Icons.Default.CheckCircle
    val label = when (state) {
        is AutoSaveTick.Counting -> stringResource(
            if (state.byPages) R.string.mushaf_autosave_counting_pages
            else R.string.mushaf_autosave_counting,
            state.secondsLeft,
        )
        AutoSaveTick.Saved -> stringResource(R.string.mushaf_autosave_saved)
        AutoSaveTick.Idle -> stringResource(R.string.mushaf_autosave_idle)
    }
    val accent = if (isCounting) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state is AutoSaveTick.Counting) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(14.dp),
                        color = accent,
                        strokeWidth = 1.5.dp,
                        trackColor = accent.copy(alpha = 0.25f),
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(
                        R.string.mushaf_autosave_content_description,
                    ),
                    modifier = Modifier.size(if (isCounting) 9.dp else 14.dp),
                    tint = accent,
                )
            }
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

@Composable
private fun BottomActionPanel(
    isBookmarked: Boolean,
    isPlaying: Boolean,
    isTranslateOpen: Boolean,
    isMemorizeOpen: Boolean,
    readerOrientation: ReaderOrientation,
    isLandscape: Boolean = false,
    landscapeZoom: Float = 1f,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTranslateToggle: () -> Unit,
    onAudioToggle: () -> Unit,
    onMemorizeToggle: () -> Unit,
    onOrientationCycle: () -> Unit,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onZoomReset: () -> Unit = {},
) {
    PanelSurface(
        modifier = Modifier.fillMaxWidth(),
    ) { compact ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLandscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    PanelIconButton(
                        icon = Icons.Default.ZoomOut,
                        contentDescription = stringResource(R.string.reading_zoom_out),
                        active = landscapeZoom < 1f,
                        onClick = onZoomOut,
                    )
                    Surface(
                        onClick = onZoomReset,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                            .copy(alpha = 0.45f),
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = "${(landscapeZoom * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    PanelIconButton(
                        icon = Icons.Default.ZoomIn,
                        contentDescription = stringResource(R.string.reading_zoom_in),
                        active = landscapeZoom > 1f,
                        onClick = onZoomIn,
                    )
                }
            }

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
}

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

                    when (selectedTab) {
                        0 -> {
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

@Composable
private fun SessionCompleteSplash(
    pagesRead: Int,
    durationMinutes: Int,
    onContinue: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    val softwareBlurBg = remember {
        if (android.os.Build.VERSION.SDK_INT >= 31) return@remember null
        try {
            val original = view.drawToBitmap()
            val blurred = softwareBlur(original, radius = 25)
            original.recycle()
            blurred.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        softwareBlurBg?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f)),
        ) {
            val isCompactHeight = maxHeight < 480.dp
            val heroSize = if (isCompactHeight) 88.dp else 120.dp
            val heroIconSize = if (isCompactHeight) 48.dp else 64.dp
            val titleStyle = if (isCompactHeight) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.displaySmall
            }
            val verticalGapHero = if (isCompactHeight) 16.dp else 24.dp
            val verticalGapStats = if (isCompactHeight) 24.dp else 32.dp
            val verticalGapButtons = if (isCompactHeight) 20.dp else 28.dp

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(heroSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(heroIconSize),
                    )
                }
                Spacer(Modifier.height(verticalGapHero))

                Text(
                    text = context.getString(R.string.session_complete_title),
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))

                Text(
                    text = context.getString(R.string.session_complete_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(verticalGapStats))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SplashStat(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            value = pagesRead.toString(),
                            label = context.getString(
                                R.string.session_complete_pages_label,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            modifier = Modifier.height(48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SplashStat(
                            icon = Icons.Default.Timer,
                            value = durationMinutes.toString(),
                            label = context.getString(
                                R.string.session_complete_minutes_label,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(verticalGapButtons))

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.session_continue_reading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = context.getString(R.string.common_close),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun softwareBlur(
    source: android.graphics.Bitmap,
    radius: Int = 25,
): android.graphics.Bitmap {
    val scale = 0.125f
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    val input = android.graphics.Bitmap.createScaledBitmap(source, w, h, true)
    val pix = IntArray(w * h)
    input.getPixels(pix, 0, w, 0, 0, w, h)

    val r = (radius * scale).toInt().coerceIn(1, minOf(w, h) / 2)
    val d = 2 * r + 1

    // ── Horizontal pass → tmp ──
    val tmp = IntArray(w * h)
    for (y in 0 until h) {
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val c = pix[y * w + i.coerceIn(0, w - 1)]
            sr += (c shr 16) and 0xFF
            sg += (c shr 8) and 0xFF
            sb += c and 0xFF
        }
        for (x in 0 until w) {
            tmp[y * w + x] = (0xFF shl 24) or
                ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
            val a = pix[y * w + (x + r + 1).coerceAtMost(w - 1)]
            val rm = pix[y * w + (x - r).coerceAtLeast(0)]
            sr += ((a shr 16) and 0xFF) - ((rm shr 16) and 0xFF)
            sg += ((a shr 8) and 0xFF) - ((rm shr 8) and 0xFF)
            sb += (a and 0xFF) - (rm and 0xFF)
        }
    }

    // ── Vertical pass → out ──
    val out = IntArray(w * h)
    for (x in 0 until w) {
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val c = tmp[i.coerceIn(0, h - 1) * w + x]
            sr += (c shr 16) and 0xFF
            sg += (c shr 8) and 0xFF
            sb += c and 0xFF
        }
        for (y in 0 until h) {
            out[y * w + x] = (0xFF shl 24) or
                ((sr / d).coerceIn(0, 255) shl 16) or
                ((sg / d).coerceIn(0, 255) shl 8) or
                (sb / d).coerceIn(0, 255)
            val a = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
            val rm = tmp[(y - r).coerceAtLeast(0) * w + x]
            sr += ((a shr 16) and 0xFF) - ((rm shr 16) and 0xFF)
            sg += ((a shr 8) and 0xFF) - ((rm shr 8) and 0xFF)
            sb += (a and 0xFF) - (rm and 0xFF)
        }
    }

    input.recycle()
    val result = android.graphics.Bitmap.createBitmap(
        w, h, android.graphics.Bitmap.Config.ARGB_8888,
    )
    result.setPixels(out, 0, w, 0, 0, w, h)
    return result
}
