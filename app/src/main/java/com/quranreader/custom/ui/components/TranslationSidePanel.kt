package com.quranreader.custom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.model.TranslationText
import com.quranreader.custom.ui.viewmodel.TranslationDisplayMode

/**
 * Vertical side slider for translations.
 *
 * Slides in from the trailing (right) edge, occupying ~62% of screen
 * width on phones (clamped to [320..480]dp). The reader page below
 * stays interactive — users can keep scrolling/highlighting verses
 * while the slider is open. A dim scrim covers the rest of the screen
 * but does NOT block touches; clicking outside the panel closes it.
 *
 * Display mode toggle (highlighted-only vs all-on-page) is rendered
 * inside the panel so the user can flip without leaving the reader.
 */
@Composable
fun TranslationSidePanel(
    visible: Boolean,
    translations: List<TranslationText>,
    highlightedAyahNumber: Int?,
    isLoading: Boolean,
    displayMode: TranslationDisplayMode,
    onDisplayModeChange: (TranslationDisplayMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val panelWidth = (screenWidth * 0.62f).coerceIn(320.dp, 480.dp)

    Box(modifier = modifier.fillMaxSize()) {
        // Light scrim — tap-anywhere-to-close. Subtle so the page stays
        // legible even while the panel is open.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(BiasAlignment(1f, 0f)),
        ) {
            val shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .shadow(elevation = 16.dp, shape = shape)
                    .clip(shape),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 4.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    Header(onDismiss = onDismiss)
                    DisplayModeRow(
                        mode = displayMode,
                        onChange = onDisplayModeChange,
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )

                    when {
                        isLoading -> LoadingState()
                        translations.isEmpty() -> EmptyState(
                            highlightedSelected = highlightedAyahNumber != null && displayMode == TranslationDisplayMode.HIGHLIGHTED_ONLY,
                        )
                        else -> {
                            // When in HIGHLIGHTED_ONLY mode the VM may
                            // still return all-page rows (reader caches
                            // everything once); filter here so the UI
                            // is the source of truth on what's visible.
                            val visibleRows = if (displayMode == TranslationDisplayMode.HIGHLIGHTED_ONLY) {
                                highlightedAyahNumber?.let { hi ->
                                    translations.filter { it.ayahNumber == hi }
                                } ?: translations
                            } else {
                                translations
                            }
                            TranslationList(
                                rows = visibleRows,
                                highlightedAyahNumber = highlightedAyahNumber,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Translation",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close translation panel",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayModeRow(
    mode: TranslationDisplayMode,
    onChange: (TranslationDisplayMode) -> Unit,
) {
    val options = listOf(
        TranslationDisplayMode.HIGHLIGHTED_ONLY to "Highlighted",
        TranslationDisplayMode.ALL_ON_PAGE to "All on page",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == mode,
                    onClick = { onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(highlightedSelected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (highlightedSelected) {
                "Tap an ayah on the page to see its translation."
            } else {
                "No translation downloaded for this language. Open Settings → Manage Translations to add one."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun TranslationList(
    rows: List<TranslationText>,
    highlightedAyahNumber: Int?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { "${it.translationId}-${it.surahNumber}-${it.ayahNumber}" }) { row ->
            TranslationRow(
                row = row,
                isHighlighted = row.ayahNumber == highlightedAyahNumber,
            )
        }
        item { Spacer(modifier = Modifier.height(48.dp)) }
    }
}

@Composable
private fun TranslationRow(
    row: TranslationText,
    isHighlighted: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isHighlighted) 2.dp else 0.dp,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = "${QuranInfo.getSurahEnglishName(row.surahNumber)} ${row.surahNumber}:${row.ayahNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (row.translationName.isNotBlank()) {
                Text(
                    text = row.translationName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = row.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

