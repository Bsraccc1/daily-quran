package com.quranreader.custom.ui.screens.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.sync.AyahKey
import com.quranreader.custom.data.memorization.MemorizationSession
import com.quranreader.custom.ui.components.animated.AnimatedCard
import java.text.DateFormat
import java.util.Date

/**
 * Memorization (hifz) history screen — Settings → Memorization History.
 *
 * Lists past sessions chronologically with surah:ayah range, repeat target, duration.
 * Tap a row to resume that session via [onResume].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationHistoryScreen(
    onNavigateBack: () -> Unit,
    onResume: (AyahKey, repeatTarget: Int, autoAdvance: Boolean) -> Unit,
    viewModel: MemorizationHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Memorization History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onResume = {
                            onResume(
                                AyahKey(session.surahStart, session.ayahStart),
                                session.repeatTarget,
                                session.autoAdvance
                            )
                        },
                        onDelete = { viewModel.delete(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: MemorizationSession,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val surahName = QuranInfo.getSurahEnglishName(session.surahStart)
    val rangeText = if (session.surahStart == session.surahEnd && session.ayahStart == session.ayahEnd) {
        "${session.surahStart}:${session.ayahStart}"
    } else {
        "${session.surahStart}:${session.ayahStart} – ${session.surahEnd}:${session.ayahEnd}"
    }
    val statusText = if (session.completedAt != null) "Completed" else "In progress"

    AnimatedCard(modifier = Modifier.fillMaxWidth(), onClick = onResume) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$surahName · $rangeText",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${session.repeatTarget}x · ${session.totalSeconds / 60} min · $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDate(session.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No memorization sessions yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Open any ayah and tap Memorize to start your first hifz session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
