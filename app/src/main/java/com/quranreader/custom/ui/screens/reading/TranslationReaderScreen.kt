package com.quranreader.custom.ui.screens.reading

import android.content.Intent as AndroidIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.ui.screens.reading.components.SurahHeaderRow
import com.quranreader.custom.ui.screens.reading.components.VerseCard
import com.quranreader.custom.ui.screens.reading.components.VerseCardCallbacks
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.TranslationReaderViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Per-juz Translation reader. Top of screen is a horizontal Juz tab
 * row (1..30); the body is a [HorizontalPager] where each page is a
 * [LazyColumn] of [VerseCard]s.
 *
 * State / event split (per `android-viewmodel` skill):
 *  - State: [TranslationReaderViewModel.juzState] / `currentJuz`.
 *  - Events: collected from [TranslationReaderViewModel.intents] and
 *    routed to [AudioViewModel] / [BookmarksViewModel] / Activity
 *    share intent / nav callback.
 */
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
) {
    val currentJuz by viewModel.currentJuz.collectAsStateWithLifecycle()
    val juzState by viewModel.juzState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pagerState = rememberPagerState(
        initialPage = (initialJuz - 1).coerceIn(0, 29),
        pageCount = { 30 },
    )

    // Pager → VM: tell the VM which juz the user just swiped to.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.selectJuz(page + 1) }
    }

    // VM intents → side effects (audio, share, navigation). Bookmark
    // toggles happen inside the VM so we don't surface them here.
    LaunchedEffect(viewModel) {
        viewModel.intents.collect { intent ->
            when (intent) {
                is TranslationReaderViewModel.Intent.PlayPage -> {
                    audioViewModel.playPage(intent.page)
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

    val callbacks = remember(viewModel) {
        VerseCardCallbacks(
            onPlay = { row -> viewModel.playAyah(row.surah, row.ayah) },
            onBookmark = { row -> viewModel.toggleBookmark(row.surah, row.ayah) },
            onShare = { row -> viewModel.shareAyah(row) },
            onJumpToMushaf = { row -> viewModel.jumpToMushaf(row.surah, row.ayah) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.reader_translation_title, currentJuz))
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
                        // Flip to Mushaf at the first visible verse of the current juz.
                        val first = juzState.rows.firstOrNull()
                        onSwitchToMushaf(first?.surah ?: 1, first?.ayah ?: 1)
                    }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.reader_toggle_mode),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // Juz tab strip ──────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = currentJuz - 1,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                for (j in 1..30) {
                    Tab(
                        selected = currentJuz == j,
                        onClick = { viewModel.selectJuz(j) },
                        text = { Text("$j") },
                    )
                }
            }

            // Pager (one page per juz) ───────────────────────────────────
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
                )
            }
        }
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
) {
    // Only the active page's [JuzUiState] is fresh; off-screen pages
    // render empty-state so we don't show stale rows from a different
    // juz. The VM only loads one juz at a time (selectJuz on swipe).
    if (!isActive) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.reader_translation_loading),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }

    val rows = state.rows
    val listState = rememberLazyListState()

    // Persist the first visible verse as the user scrolls so a flip
    // back to Mushaf lands on roughly the same place.
    val firstVisibleRow by remember(rows) {
        derivedStateOf {
            rows.getOrNull(listState.firstVisibleItemIndex)
        }
    }
    LaunchedEffect(firstVisibleRow) {
        firstVisibleRow?.let { onPositionChanged(it.surah, it.ayah) }
    }

    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        rows.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.error
                        ?: stringResource(R.string.reader_translation_arabic_unavailable, juz),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().fillMaxSize(),
            ) {
                items(items = rows, key = { row -> "${row.surah}:${row.ayah}" }) { row ->
                    if (row.isSurahStart) {
                        SurahHeaderRow(
                            surahNumber = row.surah,
                            surahNameArabic = QuranInfo.getSurahName(row.surah),
                            surahNameEnglish = QuranInfo.getSurahEnglishName(row.surah),
                        )
                    }
                    VerseCard(row = row, callbacks = callbacks)
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}
