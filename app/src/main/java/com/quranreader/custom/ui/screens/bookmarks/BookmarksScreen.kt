@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.quranreader.custom.ui.screens.bookmarks

import android.content.Intent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.model.Bookmark
import com.quranreader.custom.ui.components.animated.ChipRow
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.viewmodel.BookmarksViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bookmarks Screen — v3.0 redesign (REQ-012).
 *
 *  - Filter chips: All / This Week / This Month / By Surah
 *  - Sort dropdown: Date / Surah / Page
 *  - Swipe-to-dismiss rows: left = delete (with undo snackbar), right = share
 *  - Animated empty state w/ pulsing bookmark icon
 *  - Stagger fade-in animation for cards on entry (50ms each)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onNavigateToReading: (Int) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (bookmarks.isEmpty()) {
        EmptyBookmarksState()
        return
    }

    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(BookmarkSort.Date) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val filtered = remember(bookmarks, filter) {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000
        when (filter) {
            "This Week" -> bookmarks.filter { it.timestamp >= weekAgo }
            "This Month" -> bookmarks.filter { it.timestamp >= monthAgo }
            "By Surah" -> bookmarks.filter { it.surah != null }
            else -> bookmarks
        }
    }

    val sorted = remember(filtered, sort) {
        when (sort) {
            BookmarkSort.Date -> filtered.sortedByDescending { it.timestamp }
            // Sort by surah uses the resolved surah (per-ayah surah if
            // present, otherwise derived from page) so page-only
            // bookmarks aren't all dumped at the end of the list.
            BookmarkSort.Surah -> filtered.sortedWith(
                compareBy({ bookmarkSurahNumber(it) }, { it.ayah ?: 0 })
            )
            BookmarkSort.Page -> filtered.sortedBy { it.page }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Filter chips + sort dropdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ChipRow(
                        items = listOf("All", "This Week", "This Month", "By Surah"),
                        selected = filter,
                        onSelect = { filter = it },
                        label = { it }
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        BookmarkSort.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text("Sort by ${option.label}") },
                                leadingIcon = {
                                    if (sort == option) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Spacer(Modifier.size(24.dp))
                                    }
                                },
                                onClick = {
                                    sort = option
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(sorted, key = { _, b -> b.id }) { index, bookmark ->
                    StaggeredBookmarkRow(
                        index = index,
                        bookmark = bookmark,
                        onOpen = { onNavigateToReading(bookmark.page) },
                        onDelete = {
                            viewModel.deleteBookmark(bookmark)
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Bookmark deleted",
                                    actionLabel = "Undo",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.addBookmarkRaw(bookmark)
                                }
                            }
                        },
                        onShare = {
                            val text = buildShareText(bookmark)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share bookmark").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Resolve the surah a bookmark belongs to. Per-ayah bookmarks store
 * the surah explicitly; page-level bookmarks (surah = null) get the
 * surah that *starts on or before* the page so the bookmark UI can
 * always lead with a surah name instead of a bare page number — the
 * thing the user actually remembers.
 */
private fun bookmarkSurahNumber(bookmark: Bookmark): Int =
    bookmark.surah ?: ((1..114).lastOrNull { QuranInfo.getStartPage(it) <= bookmark.page } ?: 1)

private fun buildShareText(bookmark: Bookmark): String {
    val surahNum = bookmarkSurahNumber(bookmark)
    val surahName = QuranInfo.getSurahEnglishName(surahNum)
    val ref = if (bookmark.ayah != null) {
        "$surahName ($surahNum:${bookmark.ayah}) — page ${bookmark.page}"
    } else {
        "$surahName (Surah $surahNum) — page ${bookmark.page}"
    }
    return "Check out this bookmark in my Quran reader: $ref"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaggeredBookmarkRow(
    index: Int,
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(bookmark.id) {
        delay((index * 50L).coerceAtMost(400L))
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = Motion.standard()) +
                slideInVertically(animationSpec = Motion.standard()) { it / 4 },
        exit = fadeOut(animationSpec = Motion.short())
    ) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDelete()
                        true
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onShare()
                        false // bounce back so the card stays after share
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            },
            positionalThreshold = { totalDistance -> totalDistance * 0.4f }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = { SwipeBackground(dismissState.targetValue) },
            content = {
                BookmarkCard(
                    bookmark = bookmark,
                    onClick = onOpen,
                    onShare = onShare
                )
            }
        )
    }
}

@Composable
private fun SwipeBackground(target: SwipeToDismissBoxValue) {
    when (target) {
        SwipeToDismissBoxValue.EndToStart -> SwipeBackgroundRow(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.Delete,
            label = "Delete",
            alignment = Alignment.CenterEnd
        )
        SwipeToDismissBoxValue.StartToEnd -> SwipeBackgroundRow(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Default.Share,
            label = "Share",
            alignment = Alignment.CenterStart
        )
        SwipeToDismissBoxValue.Settled -> {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SwipeBackgroundRow(
    color: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    alignment: Alignment
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = color, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (alignment == Alignment.CenterStart) {
                Icon(imageVector = icon, contentDescription = label, tint = contentColor)
                Text(text = label, style = MaterialTheme.typography.titleSmall, color = contentColor, fontWeight = FontWeight.SemiBold)
            } else {
                Text(text = label, style = MaterialTheme.typography.titleSmall, color = contentColor, fontWeight = FontWeight.SemiBold)
                Icon(imageVector = icon, contentDescription = label, tint = contentColor)
            }
        }
    }
}

/**
 * Bookmark row leading with the **surah name** instead of the page
 * number. Page numbers are an implementation detail of how the
 * Mushaf is laid out — when users come back to "the bookmark I made
 * in Al-Baqarah", they remember the surah and (sometimes) the
 * verse, never the absolute page index. Layout:
 *
 *  - 56 dp avatar with the surah *number* (1–114) — visually
 *    equivalent to the old page badge but ties straight to the
 *    canonical reference everyone knows.
 *  - Title row: surah English name + "(N)" surah number, bold.
 *  - Subtitle row: "Verse N · Page P" when the bookmark stores a
 *    specific ayah; just "Page P" for page-level bookmarks. Both
 *    make page secondary so the user reads "what" (surah) before
 *    "where in the binding" (page).
 *  - Footer row: relative timestamp.
 */
@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    val surahNum = bookmarkSurahNumber(bookmark)
    val surahName = remember(surahNum) { QuranInfo.getSurahEnglishName(surahNum) }
    val secondaryLine = bookmark.ayah?.let { ayah ->
        "Verse $ayah · Page ${bookmark.page}"
    } ?: "Page ${bookmark.page}"

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        surahNum.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$surahName ($surahNum)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    secondaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatRelativeTime(bookmark.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyBookmarksState() {
    val transition = rememberInfiniteTransition(label = "EmptyBookmarks.pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EmptyBookmarks.scale"
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulse),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                "No Bookmarks Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tap any ayah while reading\nto bookmark it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatRelativeTime(epochMs: Long): String {
    val diffSec = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60} min ago"
        diffSec < 86400 -> "${diffSec / 3600} h ago"
        diffSec < 604800 -> "${diffSec / 86400} d ago"
        else -> "${diffSec / 604800} w ago"
    }
}

enum class BookmarkSort(val label: String) { Date("Date"), Surah("Surah"), Page("Page") }
