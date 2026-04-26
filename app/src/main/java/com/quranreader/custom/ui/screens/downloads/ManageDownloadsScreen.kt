package com.quranreader.custom.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.data.audio.download.DownloadedSurah
import com.quranreader.custom.ui.components.animated.AnimatedCard
import com.quranreader.custom.ui.components.animated.ExpandableSection
import java.text.DateFormat
import java.util.Date

/**
 * Manage Audio Downloads screen — Settings sub-screen.
 *
 * Lists all downloaded surahs grouped by reciter with size, date, delete action.
 * Top card shows total cache size + Delete-All.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDownloadsScreen(
    onNavigateBack: () -> Unit,
    onBrowseSurahs: () -> Unit,
    viewModel: ManageDownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadedSurah?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Audio Downloads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.byReciter.isEmpty()) {
                EmptyDownloadsState(onBrowseSurahs = onBrowseSurahs)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        TotalSizeCard(
                            totalBytes = state.totalBytes,
                            onDeleteAll = { showDeleteAllDialog = true }
                        )
                    }

                    state.byReciter.forEach { (reciterId, rows) ->
                        item(key = "header-$reciterId") {
                            ExpandableSection(
                                title = rows.firstOrNull()?.reciterName ?: reciterId,
                                initiallyExpanded = true
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rows.forEach { row ->
                                        SurahRow(
                                            row = row,
                                            onDelete = { pendingDelete = row.item }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete all downloads?") },
            text = {
                Text("This will free ${formatBytes(state.totalBytes)} but you'll need to re-download surahs to play offline.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showDeleteAllDialog = false
                }) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this download?") },
            text = { Text("Free ${formatBytes(item.totalBytes)}.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSurah(item)
                    pendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TotalSizeCard(totalBytes: Long, onDeleteAll: () -> Unit) {
    AnimatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total downloads", style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatBytes(totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = onDeleteAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.height(0.dp).padding(end = 4.dp))
                Text("Delete all")
            }
        }
    }
}

@Composable
private fun SurahRow(row: DownloadedSurahRow, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${row.item.surahNumber}. ${row.surahName}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${row.item.ayahCount} ayahs · ${formatBytes(row.item.totalBytes)} · ${formatDate(row.item.downloadedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun EmptyDownloadsState(onBrowseSurahs: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.MusicOff,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No audio downloaded yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Tap the download icon next to any surah to save its recitation for offline listening.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ElevatedButton(onClick = onBrowseSurahs) {
                Text("Browse surahs")
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "${bytes / 1024} KB"
    }
}

internal fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
