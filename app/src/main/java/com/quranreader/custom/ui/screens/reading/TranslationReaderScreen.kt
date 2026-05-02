package com.quranreader.custom.ui.screens.reading

import android.content.Intent as AndroidIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.ReciterConfig
import com.quranreader.custom.data.preferences.ReaderOrientation
import com.quranreader.custom.ui.components.TranslationEditionsDialog

import com.quranreader.custom.ui.screens.reading.components.SurahHeaderRow
import com.quranreader.custom.ui.screens.reading.components.VerseCard
import com.quranreader.custom.ui.screens.reading.components.VerseCardCallbacks
import com.quranreader.custom.ui.screens.reading.components.VerseCardSkeleton
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.TranslationReaderViewModel
import com.quranreader.custom.ui.viewmodel.TranslationViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Translation reader.
//
// Top bar carries the only persistent chrome:
//   [back] [Juz N / Juz N of 30]      [swap-mode] [orientation] [tune]
//
// The previous slide-up action panel is gone. Its actions moved to:
//   * Back: top bar nav slot.
//   * Memorize: per-verse icon on each VerseCard's action row, so the
//     overlay opens with the verse the user pointed at instead of
//     guessing from scroll position.
//   * Orientation: top-bar action that cycles AUTO -> portrait ->
//     landscape, with the icon swapping per state.
//
// The Tune icon opens [ReaderQuickSettingsSheet], a bottom sheet with
// reciter chips and a translation-edition row.

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun TranslationReaderScreen(
    initialJuz: Int = 1,
    onBack: () -> Unit,
    onSwitchToMushaf: (surah: Int, ayah: Int) -> Unit,
    viewModel: TranslationReaderViewModel = hiltViewModel(),
    audioViewModel: AudioViewModel = hiltViewModel(),
    readingViewModel: ReadingViewModel = hiltViewModel(),
    translationViewModel: TranslationViewModel = hiltViewModel(),
) {
    val currentJuz by viewModel.currentJuz.collectAsStateWithLifecycle()
    val juzState by viewModel.juzState.collectAsStateWithLifecycle()
    val readerOrientation by readingViewModel.readerOrientation.collectAsStateWithLifecycle()
    val arabicFontSize by readingViewModel.arabicFontSize.collectAsStateWithLifecycle()
    val translationFontSize by readingViewModel.translationFontSize.collectAsStateWithLifecycle()
    val autoScrollSpeed by readingViewModel.autoScrollSpeed.collectAsStateWithLifecycle()
    // Transient play/pause state — not persisted, resets when screen closes.
    var isAutoScrolling by rememberSaveable { mutableStateOf(false) }

    val editions by translationViewModel.editions.collectAsStateWithLifecycle()
    val translationEditionId by translationViewModel.translationEditionId.collectAsStateWithLifecycle()
    val downloadProgressMap by translationViewModel.downloadProgress.collectAsStateWithLifecycle()
    val catalogueRefreshing by translationViewModel.catalogueRefreshing.collectAsStateWithLifecycle()
    val currentEditionName = remember(editions, translationEditionId) {
        editions.firstOrNull { it.editionId == translationEditionId }?.name
    }

    val currentReciter by audioViewModel.currentReciter.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = (initialJuz - 1).coerceIn(0, 29),
        pageCount = { 30 },
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.selectJuz(page + 1) }
    }

    LaunchedEffect(viewModel) {
        viewModel.intents.collect { intent ->
            when (intent) {
                is TranslationReaderViewModel.Intent.PlayAyah -> {
                    val ayahCount = QuranInfo.getAyahCount(intent.surah)
                    audioViewModel.playRange(intent.surah, intent.ayah, ayahCount)
                }
                is TranslationReaderViewModel.Intent.ShareAyah -> {
                    val share = AndroidIntent(AndroidIntent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(AndroidIntent.EXTRA_TEXT, intent.text)
                    }
                    context.startActivity(AndroidIntent.createChooser(share, null))
                }
                is TranslationReaderViewModel.Intent.JumpToMushaf -> {
                    onSwitchToMushaf(intent.surah, intent.ayah)
                }
            }
        }
    }

    // Mirror the Mushaf reader's orientation override so locking
    // landscape from one mode survives a swap to the other.
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

    var memorizeSeed by remember {
        mutableStateOf<com.quranreader.custom.data.audio.sync.AyahKey?>(null)
    }

    val callbacks = remember(viewModel) {
        VerseCardCallbacks(
            onPlay = { row -> viewModel.playAyah(row.surah, row.ayah) },
            onBookmark = { row -> viewModel.toggleBookmark(row.surah, row.ayah) },
            onShare = { row -> viewModel.shareAyah(row) },
            onMemorize = { row ->
                memorizeSeed = com.quranreader.custom.data.audio.sync.AyahKey(row.surah, row.ayah)
            },
            onJumpToMushaf = { row -> viewModel.jumpToMushaf(row.surah, row.ayah) },
        )
    }

    var showQuickSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showEditionsDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                R.string.reader_translation_juz_label,
                                currentJuz,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.reader_translation_juz_progress,
                                currentJuz,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val first = juzState.rows.firstOrNull()
                        onSwitchToMushaf(first?.surah ?: 1, first?.ayah ?: 1)
                    }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.reader_toggle_mode),
                        )
                    }
                    OrientationActionIcon(
                        orientation = readerOrientation,
                        onClick = { readingViewModel.cycleReaderOrientation() },
                    )
                    // Auto-scroll toggle
                    IconButton(onClick = { isAutoScrolling = !isAutoScrolling }) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Default.Pause
                                          else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (isAutoScrolling) R.string.reader_autoscroll_on
                                else R.string.reader_autoscroll_off,
                            ),
                            tint = if (isAutoScrolling) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = {
                        showQuickSettingsSheet = true
                        translationViewModel.refreshCatalogue()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(
                                R.string.reader_quick_settings_title,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Material 3 scrollable tab row instead of a custom pill rail.
            // M3 handles selection, animation, and indicator alignment
            // for free, and looks consistent with the rest of the app.
            PrimaryScrollableTabRow(
                selectedTabIndex = (currentJuz - 1).coerceIn(0, 29),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                },
            ) {
                (1..30).forEach { juz ->
                    Tab(
                        selected = juz == currentJuz,
                        onClick = {
                            viewModel.selectJuz(juz)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(juz - 1)
                            }
                        },
                        text = {
                            Text(
                                text = juz.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (juz == currentJuz) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Medium
                                },
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                val pageJuz = pageIndex + 1
                JuzPage(
                    juz = pageJuz,
                    state = juzState,
                    callbacks = callbacks,
                    isActive = pageJuz == currentJuz,
                    onPositionChanged = { surah, ayah ->
                        viewModel.rememberPosition(surah, ayah)
                    },
                    arabicFontSizeSp = arabicFontSize,
                    translationFontSizeSp = translationFontSize,
                    // Only the visible juz page auto-scrolls.
                    isAutoScrolling = isAutoScrolling && pageJuz == currentJuz,
                    autoScrollSpeedDpPerSec = autoScrollSpeed * 8f,
                    onAutoScrollEnded = { isAutoScrolling = false },
                )
            }
        }
    }

    memorizeSeed?.let { seed ->
        com.quranreader.custom.ui.screens.memorization.MemorizationOverlay(
            initialAyah = seed,
            onDismiss = { memorizeSeed = null },
        )
    }

    if (showQuickSettingsSheet) {
        ReaderQuickSettingsSheet(
            currentReciter = currentReciter,
            availableReciters = audioViewModel.availableReciters,
            currentEditionName = currentEditionName,
            onSelectReciter = { reciterId ->
                audioViewModel.setReciter(reciterId)
            },
            onChooseTranslation = { showEditionsDialog = true },
                arabicFontSize = arabicFontSize,
                translationFontSize = translationFontSize,
                onArabicFontSizeChange = { readingViewModel.setArabicFontSize(it) },
                onTranslationFontSizeChange = { readingViewModel.setTranslationFontSize(it) },
                autoScrollSpeed = autoScrollSpeed,
                onAutoScrollSpeedChange = { readingViewModel.setAutoScrollSpeed(it) },
                onDismiss = { showQuickSettingsSheet = false },
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
                showQuickSettingsSheet = false
            },
            onDownload = { edition -> translationViewModel.downloadEdition(edition) },
            onDelete = { editionId -> translationViewModel.deleteEdition(editionId) },
            onRefresh = { translationViewModel.refreshCatalogue() },
            onDismiss = { showEditionsDialog = false },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun JuzPage(
    juz: Int,
    state: TranslationReaderViewModel.JuzUiState,
    callbacks: VerseCardCallbacks,
    isActive: Boolean,
    onPositionChanged: (surah: Int, ayah: Int) -> Unit,
    arabicFontSizeSp: Float = 26f,
    translationFontSizeSp: Float = 16f,
    isAutoScrolling: Boolean = false,
    autoScrollSpeedDpPerSec: Float = 24f,
    onAutoScrollEnded: () -> Unit = {},
) {
    if (!isActive) {
        SkeletonList()
        return
    }

    val rows = state.rows
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val firstVisibleRow by remember(rows) {
        derivedStateOf { rows.getOrNull(listState.firstVisibleItemIndex) }
    }
    LaunchedEffect(firstVisibleRow) {
        firstVisibleRow?.let { onPositionChanged(it.surah, it.ayah) }
    }

    // Auto-scroll coroutine. Runs at ~60 fps, skips frames when the
    // user is manually dragging so the two scroll inputs don't fight.
    // Stops itself when the bottom of the list is reached.
    LaunchedEffect(isAutoScrolling, autoScrollSpeedDpPerSec) {
        if (!isAutoScrolling) return@LaunchedEffect
        // Convert dp/s to px per 16 ms frame.
        val pxPerFrame = with(density) { (autoScrollSpeedDpPerSec / 60f).dp.toPx() }
        while (isActive) {
            if (!listState.isScrollInProgress) {
                val consumed = listState.scrollBy(pxPerFrame)
                // scrollBy returns 0 when there is nothing left to scroll.
                if (consumed == 0f && listState.layoutInfo.totalItemsCount > 0) {
                    onAutoScrollEnded()
                    break
                }
            }
            delay(16L)
        }
    }

    when {
        state.isLoading -> SkeletonList()
        rows.isEmpty() -> EmptyJuzState(juz = juz, error = state.error)
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            ) {
                items(items = rows, key = { row -> "${row.surah}:${row.ayah}" }) { row ->
                    if (row.isSurahStart) {
                        SurahHeaderRow(
                            surahNumber = row.surah,
                            surahNameArabic = QuranInfo.getSurahName(row.surah),
                            surahNameEnglish = QuranInfo.getSurahEnglishName(row.surah),
                            ayahCount = QuranInfo.getAyahCount(row.surah),
                            isMakki = QuranInfo.isMakki(row.surah),
                        )
                    }
                    VerseCard(
                            row = row,
                            callbacks = callbacks,
                            arabicFontSizeSp = arabicFontSizeSp,
                            translationFontSizeSp = translationFontSizeSp,
                        )
                }
            }
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
    ) {
        repeat(3) {
            VerseCardSkeleton()
        }
    }
}

@Composable
private fun EmptyJuzState(juz: Int, error: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.AutoStories,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.reader_translation_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = error
                ?: stringResource(R.string.reader_translation_arabic_unavailable, juz),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Top-bar orientation toggle ──────────────────────────────────────

/**
 * Cycles AUTO -> PORTRAIT -> LANDSCAPE -> AUTO. The icon swaps per
 * state so the current mode is legible at a glance.
 */
@Composable
private fun OrientationActionIcon(
    orientation: ReaderOrientation,
    onClick: () -> Unit,
) {
    val (icon, descRes) = when (orientation) {
        ReaderOrientation.AUTO ->
            Icons.Default.ScreenRotation to R.string.reader_orientation_auto
        ReaderOrientation.PORTRAIT ->
            Icons.Default.ScreenLockPortrait to R.string.reader_orientation_portrait
        ReaderOrientation.LANDSCAPE ->
            Icons.Default.ScreenLockLandscape to R.string.reader_orientation_landscape
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(descRes),
            tint = if (orientation == ReaderOrientation.AUTO) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

// ── Quick-settings sheet ────────────────────────────────────────────

/**
 * Modal bottom sheet exposing the two reader-time settings users
 * tweak most often: which reciter plays the per-verse audio, and
 * which translation edition the rows display. Reciter is a
 * `FilterChip` row, translation is a `ListItem` that opens the
 * shared [TranslationEditionsDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderQuickSettingsSheet(
    currentReciter: ReciterConfig,
    availableReciters: List<ReciterConfig>,
    currentEditionName: String?,
    onSelectReciter: (String) -> Unit,
    onChooseTranslation: () -> Unit,
    arabicFontSize: Float,
    translationFontSize: Float,
    onArabicFontSizeChange: (Float) -> Unit,
    onTranslationFontSizeChange: (Float) -> Unit,
    autoScrollSpeed: Float,
    onAutoScrollSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Read screen width from the current configuration so the sheet
    // always spans the full display width on every device size and
    // orientation — phone portrait/landscape, tablet, foldable.
    // LocalConfiguration recomposes automatically on rotation, so
    // sheetMaxWidth stays correct without any manual listener.
    val sheetMaxWidth = LocalConfiguration.current.screenWidthDp.dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_quick_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            Text(
                text = stringResource(R.string.reader_quick_settings_reciter),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            ) {
                items(items = availableReciters, key = { it.id }) { reciter ->
                    FilterChip(
                        selected = reciter.id == currentReciter.id,
                        onClick = { onSelectReciter(reciter.id) },
                        label = { Text(text = reciter.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.reader_quick_settings_translation),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = currentEditionName
                            ?: stringResource(R.string.reader_quick_settings_translation_none),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.reader_quick_settings_translation_browse),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChooseTranslation),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.reader_quick_settings_font_size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            // Arabic font size — label + current value on one line, slider below
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reader_quick_settings_font_arabic),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${arabicFontSize.toInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = arabicFontSize,
                    onValueChange = onArabicFontSizeChange,
                    valueRange = 18f..40f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Translation font size
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reader_quick_settings_font_translation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${translationFontSize.toInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = translationFontSize,
                    onValueChange = onTranslationFontSizeChange,
                    valueRange = 12f..24f,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))

            // Auto-scroll speed
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.reader_quick_settings_autoscroll_speed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Speed label: Slow / Medium / Fast
                    val speedLabel = when {
                        autoScrollSpeed <= 2f  -> stringResource(R.string.reader_autoscroll_slow)
                        autoScrollSpeed >= 8f  -> stringResource(R.string.reader_autoscroll_fast)
                        else                   -> stringResource(R.string.reader_autoscroll_medium)
                    }
                    Text(
                        text = speedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = autoScrollSpeed.toInt().toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = autoScrollSpeed,
                    onValueChange = onAutoScrollSpeedChange,
                    valueRange = 1f..10f,
                    steps = 8,   // 10 positions: 1,2,3,4,5,6,7,8,9,10
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
