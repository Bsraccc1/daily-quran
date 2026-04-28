package com.quranreader.custom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.quranreader.custom.data.model.TranslationEdition
import com.quranreader.custom.ui.viewmodel.TranslationViewModel.DownloadProgress

/**
 * Edition catalogue dialog. Lists every translation quran.com hosts,
 * shows download / installed / delete actions per row, and lets the
 * user pick the edition currently surfaced in the reader's
 * translation panel.
 *
 * Rendered as a regular [AlertDialog] (not a sheet) so it sits above
 * the rest of the reader chrome without committing to a particular
 * layout. The dialog uses the platform default sizing — on phones
 * that's roughly 90% width × 85% height which is plenty for a
 * scrollable list of ~140 editions.
 */
@Composable
fun TranslationEditionsDialog(
    editions: List<TranslationEdition>,
    selectedEditionId: Int,
    downloadProgress: Map<Int, DownloadProgress>,
    isCatalogueRefreshing: Boolean,
    onSelect: (TranslationEdition) -> Unit,
    onDownload: (TranslationEdition) -> Unit,
    onDelete: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(editions, query) {
        if (query.isBlank()) editions
        else editions.filter { e ->
            e.name.contains(query, ignoreCase = true) ||
                (e.languageName?.contains(query, ignoreCase = true) == true) ||
                (e.authorName?.contains(query, ignoreCase = true) == true)
        }
    }

    // Constrain the catalogue dialog to the shared responsive width
    // tier table. Without this, `usePlatformDefaultWidth = false` lets
    // the AlertDialog stretch to the full screen width, which on a 10"
    // tablet leaves a long, thin row of editions that scans poorly.
    // We pull the screen width from [LocalConfiguration] (rather than
    // a `BoxWithConstraints` wrapper) because `AlertDialog` renders in
    // its own platform window — measuring the parent layout is not a
    // reliable proxy for the dialog's container width.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val dialogMaxWidth = responsivePanelMaxWidth(screenWidth)

    // Resolve the user's UI language (e.g. "en", "id") to the same
    // English language name the editions catalogue uses ("english",
    // "indonesian"). Editions stored in Room use the lowercase English
    // language name supplied by quran.com — `Locale#getDisplayLanguage`
    // with a fixed `Locale.ENGLISH` argument gives us a stable match
    // key without hardcoding a per-language switch.
    val preferredLanguageKey = remember(configuration) {
        val tag = configuration.locales[0].language
        java.util.Locale(tag)
            .getDisplayLanguage(java.util.Locale.ENGLISH)
            .lowercase()
            .takeIf { it.isNotBlank() }
    }

    // Pre-compute the language groups once per filter pass. Each entry
    // is `(key, displayLabel, editions)` where `key` is the lowercase
    // English language name (or empty for unknown), `displayLabel` is
    // the title-cased name shown in the section header, and `editions`
    // is the slice of the filtered list that belongs to that bucket.
    // Sort order: the user's UI language first, then alphabetical by
    // language, with the unknown/empty bucket pinned to the bottom.
    val groups = remember(filtered, preferredLanguageKey) {
        groupEditionsByLanguage(filtered, preferredLanguageKey)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(
                min = MIN_PANEL_WIDTH.coerceAtMost(screenWidth),
                max = dialogMaxWidth,
            )
            .fillMaxWidth(),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Translations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = !isCatalogueRefreshing) {
                    if (isCatalogueRefreshing) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh from quran.com")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search editions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default,
                )

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (editions.isEmpty())
                                "Tap the refresh icon to load translations from quran.com."
                            else
                                "No translations match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 480.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        groupedEditions(
                            groups = groups,
                            selectedEditionId = selectedEditionId,
                            downloadProgress = downloadProgress,
                            onSelect = onSelect,
                            onDownload = onDownload,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

/**
 * One language bucket inside the editions catalogue. `key` is the
 * lowercase English language name (e.g. "english", "indonesian") or
 * empty when the underlying [TranslationEdition.languageName] is null
 * — the empty bucket renders under the catch-all "Other" header.
 */
private data class LanguageGroup(
    val key: String,
    val displayLabel: String,
    val editions: List<TranslationEdition>,
)

private fun groupEditionsByLanguage(
    filtered: List<TranslationEdition>,
    preferredLanguageKey: String?,
): List<LanguageGroup> {
    if (filtered.isEmpty()) return emptyList()
    val byKey: Map<String, List<TranslationEdition>> = filtered.groupBy { edition ->
        edition.languageName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase()
            ?: ""
    }
    return byKey.entries
        .sortedWith(
            compareBy<Map.Entry<String, List<TranslationEdition>>>(
                // Tier: preferred (0) < alphabetical (1) < unknown bucket (2).
                { entry ->
                    when {
                        entry.key.isEmpty() -> 2
                        entry.key == preferredLanguageKey -> 0
                        else -> 1
                    }
                },
                { it.key },
            ),
        )
        .map { (key, editions) ->
            LanguageGroup(
                key = key,
                displayLabel = if (key.isEmpty()) "Other"
                else key.replaceFirstChar { it.uppercase() },
                editions = editions,
            )
        }
}

private fun LazyListScope.groupedEditions(
    groups: List<LanguageGroup>,
    selectedEditionId: Int,
    downloadProgress: Map<Int, DownloadProgress>,
    onSelect: (TranslationEdition) -> Unit,
    onDownload: (TranslationEdition) -> Unit,
    onDelete: (Int) -> Unit,
) {
    groups.forEachIndexed { index, group ->
        item(key = "lang-header-${group.key.ifEmpty { "other" }}") {
            LanguageHeader(
                label = group.displayLabel,
                count = group.editions.size,
                isFirst = index == 0,
            )
        }
        items(group.editions, key = { it.editionId }) { edition ->
            EditionRow(
                edition = edition,
                isSelected = edition.editionId == selectedEditionId,
                progress = downloadProgress[edition.editionId],
                onSelect = { onSelect(edition) },
                onDownload = { onDownload(edition) },
                onDelete = { onDelete(edition.editionId) },
            )
        }
    }
}

@Composable
private fun LanguageHeader(
    label: String,
    count: Int,
    isFirst: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                color = cs.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = if (isFirst) 0.dp else 0.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun EditionRow(
    edition: TranslationEdition,
    isSelected: Boolean,
    progress: DownloadProgress?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val containerColor = if (isSelected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.5f)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
        onClick = if (edition.isDownloaded) onSelect else onDownload,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        edition.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        edition.languageName?.replaceFirstChar { it.uppercase() },
                        edition.authorName,
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                EditionAction(
                    edition = edition,
                    progress = progress,
                    onDownload = onDownload,
                    onDelete = onDelete,
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress.fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
    }
}

@Composable
private fun EditionAction(
    edition: TranslationEdition,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    when {
        progress != null -> {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
        edition.isDownloaded -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> {
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Download")
            }
        }
    }
}
