package com.quranreader.custom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.model.TranslationEdition
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.data.preferences.TranslationScope

/**
 * In-reader translation panel — slides up from the bottom and caps at
 * ~45% of the available height so the mushaf page underneath stays
 * visible (and tappable for selecting the next ayah).
 *
 * The panel is *not* a [androidx.compose.material3.ModalBottomSheet]
 * by design — modal sheets dim the rest of the screen and steal
 * focus, which contradicts the "see the page and the translation at
 * the same time" requirement. Instead this is a plain composable
 * positioned at the bottom of a `Box`, animated in/out via
 * [AnimatedVisibility].
 *
 * Pass [scope] = [TranslationScope.HIGHLIGHTED_ONLY] to filter the
 * list down to the user's currently selected ayah, or
 * [TranslationScope.ENTIRE_PAGE] to render every translated verse on
 * the page with the highlighted one accented.
 */
@Composable
fun TranslationPanel(
    visible: Boolean,
    translations: List<TranslationText>,
    highlightedAyahNumber: Int?,
    highlightedSurahNumber: Int?,
    scope: TranslationScope,
    isLoading: Boolean,
    isEditionInstalled: Boolean,
    currentEdition: TranslationEdition?,
    onScopeToggle: () -> Unit,
    onEditionsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(220)) { it },
        exit = slideOutVertically(animationSpec = tween(180)) { it },
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            // Centre the floating sheet on tablets / wide windows.
            // Without this the panel would left-anchor inside its
            // container — the mushaf is already centred and a left-
            // aligned panel collides with the page chrome.
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Cap the panel height at 45% of the *available* container
            // height (which is already the parent reader Box). On tall
            // phones this gives ~360-400 dp of translation real estate;
            // on small landscape phones it caps at the smaller side so
            // the mushaf page is never crowded out entirely.
            val maxPanelHeight = maxHeight * 0.45f
            // Width tier table shared with the rest of the reader
            // chrome: 480 dp on phones, 560 dp on small tablets, 640 dp
            // on large tablets / unfolded foldables. Without the cap
            // the translation sheet would span the full width of a 10"
            // tablet — readable, but visually disconnected from the
            // mushaf which is itself centred.
            val maxPanelWidth = com.quranreader.custom.ui.components.responsivePanelMaxWidth(maxWidth)

            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(
                        min = com.quranreader.custom.ui.components.MIN_PANEL_WIDTH.coerceAtMost(maxWidth),
                        max = maxPanelWidth,
                    )
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = maxPanelHeight)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.4f),
                    ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                        )
                    }

                    // Header: edition chip + scope toggle + close
                    TranslationPanelHeader(
                        currentEdition = currentEdition,
                        scope = scope,
                        onScopeToggle = onScopeToggle,
                        onEditionsClick = onEditionsClick,
                        onClose = onDismiss,
                    )

                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading -> LoadingState()
                            !isEditionInstalled -> NotInstalledState(onEditionsClick = onEditionsClick)
                            else -> {
                                val visibleRows = remember(translations, scope, highlightedSurahNumber, highlightedAyahNumber) {
                                    if (scope == TranslationScope.HIGHLIGHTED_ONLY &&
                                        highlightedSurahNumber != null && highlightedAyahNumber != null
                                    ) {
                                        translations.filter {
                                            it.surahNumber == highlightedSurahNumber &&
                                                it.ayahNumber == highlightedAyahNumber
                                        }
                                    } else {
                                        translations
                                    }
                                }
                                TranslationList(
                                    rows = visibleRows,
                                    highlightedSurahNumber = highlightedSurahNumber,
                                    highlightedAyahNumber = highlightedAyahNumber,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationPanelHeader(
    currentEdition: TranslationEdition?,
    scope: TranslationScope,
    onScopeToggle: () -> Unit,
    onEditionsClick: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Edition chip — taps open the editions picker
        AssistChip(
            onClick = onEditionsClick,
            leadingIcon = {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = {
                Text(
                    text = currentEdition?.name ?: "Translation",
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            modifier = Modifier.weight(1f, fill = false),
        )

        // Scope toggle (highlighted-only ↔ entire page)
        AssistChip(
            onClick = onScopeToggle,
            leadingIcon = {
                Icon(
                    if (scope == TranslationScope.HIGHLIGHTED_ONLY) Icons.Default.MenuBook
                    else Icons.Default.ViewAgenda,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = {
                Text(
                    if (scope == TranslationScope.HIGHLIGHTED_ONLY) "Single" else "Page",
                    maxLines = 1,
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )

        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close translation panel")
        }
    }
}

@Composable
private fun TranslationList(
    rows: List<TranslationText>,
    highlightedSurahNumber: Int?,
    highlightedAyahNumber: Int?,
) {
    if (rows.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No translation available for this ayah.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // Auto-scroll the highlighted row into view (when scope = page).
    LaunchedEffect(highlightedSurahNumber, highlightedAyahNumber, rows) {
        if (highlightedSurahNumber == null || highlightedAyahNumber == null) return@LaunchedEffect
        val index = rows.indexOfFirst {
            it.surahNumber == highlightedSurahNumber && it.ayahNumber == highlightedAyahNumber
        }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { "${it.surahNumber}_${it.ayahNumber}" }) { row ->
            val accent = row.surahNumber == highlightedSurahNumber &&
                row.ayahNumber == highlightedAyahNumber
            TranslationRow(row = row, accent = accent)
        }
    }
}

@Composable
private fun TranslationRow(row: TranslationText, accent: Boolean) {
    val cs = MaterialTheme.colorScheme
    val container = if (accent) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.45f)
    val labelColor = if (accent) cs.onPrimaryContainer else cs.onSurfaceVariant
    val textColor = if (accent) cs.onPrimaryContainer else cs.onSurface

    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${QuranInfo.getSurahEnglishName(row.surahNumber)} · ${row.surahNumber}:${row.ayahNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = row.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun NotInstalledState(onEditionsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Translate,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This translation isn't downloaded yet.",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick an edition to get started.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onEditionsClick) {
            Text("Browse translations")
        }
    }
}
