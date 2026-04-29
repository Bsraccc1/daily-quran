package com.quranreader.custom.ui.screens.translations

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.data.model.AvailableTranslation
import com.quranreader.custom.ui.viewmodel.TranslationViewModel

/**
 * Translation manager — list every edition advertised by quran.com,
 * grouped by language, with per-edition download / delete actions.
 *
 * Reached from Settings → "Manage Translations".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: TranslationViewModel = hiltViewModel(),
) {
    val available by viewModel.availableTranslations.collectAsState()
    val downloaded by viewModel.downloadedTranslationIds.collectAsState()
    val active by viewModel.activeTranslationIds.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()

    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<AvailableTranslation?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshAvailableTranslations() }

    val downloadedSet = remember(downloaded) { downloaded.toSet() }
    val activeSet = remember(active) { active.toSet() }

    val filtered = remember(available, query) {
        if (query.isBlank()) available else {
            val q = query.trim().lowercase()
            available.filter {
                it.name.lowercase().contains(q) ||
                    it.authorName.lowercase().contains(q) ||
                    it.languageName.lowercase().contains(q) ||
                    it.languageCode.lowercase().contains(q)
            }
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.languageName.ifBlank { it.languageCode } }
            .toSortedMap()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Translations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (available.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search by language or author") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            singleLine = true,
                        )
                    }

                    item {
                        ActiveSummary(
                            availableTranslations = available,
                            activeIds = activeSet,
                            downloadedIds = downloadedSet,
                        )
                    }

                    grouped.forEach { (languageName, editions) ->
                        item(key = "header-$languageName") {
                            Text(
                                text = languageName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(editions, key = { "ed-${it.id}" }) { edition ->
                            EditionRow(
                                edition = edition,
                                isDownloaded = edition.id in downloadedSet,
                                isActive = edition.id in activeSet,
                                progress = progress[edition.id],
                                onDownload = { viewModel.downloadTranslationEdition(edition) },
                                onDelete = { pendingDelete = edition },
                                onToggleActive = {
                                    val newSet = if (edition.id in activeSet) activeSet - edition.id
                                    else activeSet + edition.id
                                    viewModel.setActiveTranslationIds(newSet.toList())
                                },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(48.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { edition ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete '${edition.name}'?") },
            text = { Text("This removes ~6,236 ayahs from the device. You can re-download anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTranslationEdition(edition.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ActiveSummary(
    availableTranslations: List<AvailableTranslation>,
    activeIds: Set<Int>,
    downloadedIds: Set<Int>,
) {
    val activeEditions = remember(availableTranslations, activeIds) {
        availableTranslations.filter { it.id in activeIds }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Currently active",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (activeEditions.isEmpty()) {
                Text(
                    text = "No translation selected. Tap a row below to activate one for the side panel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeEditions.forEach { ed ->
                        AssistChip(
                            onClick = {},
                            label = { Text(ed.authorName.ifBlank { ed.name }) },
                            leadingIcon = {
                                if (ed.id in downloadedIds) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditionRow(
    edition: AvailableTranslation,
    isDownloaded: Boolean,
    isActive: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = edition.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (edition.authorName.isNotBlank() && edition.authorName != edition.name) {
                        Text(
                            text = edition.authorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                if (isDownloaded) {
                    if (isActive) {
                        ElevatedAssistChip(
                            onClick = onToggleActive,
                            label = { Text("Active") },
                            leadingIcon = {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                    } else {
                        AssistChip(
                            onClick = onToggleActive,
                            label = { Text("Activate") },
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                } else if (progress != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(80.dp)
                                .height(6.dp),
                        )
                    }
                } else {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.0.dp)
}
