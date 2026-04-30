package com.quranreader.custom.ui.screens.reading.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranreader.custom.R
import com.quranreader.custom.ui.components.mushaf.UthmanicHafsFontFamily
import com.quranreader.custom.ui.viewmodel.TranslationReaderViewModel.VerseRow

/**
 * Stable wrapper around the per-card row callbacks so Compose's
 * stability inference doesn't mark every parent recomposition as
 * "unstable" and force `VerseCard` to re-render. All four lambdas are
 * captured as method references in the screen and the wrapper itself
 * is `@Immutable`.
 */
@Immutable
data class VerseCardCallbacks(
    val onPlay: (VerseRow) -> Unit,
    val onBookmark: (VerseRow) -> Unit,
    val onShare: (VerseRow) -> Unit,
    val onJumpToMushaf: (VerseRow) -> Unit,
)

/**
 * Per-verse card matching the screenshot supplied by the user:
 *
 *   ┌──────────────────────────────────────┐
 *   │ (surah header — only on isSurahStart) │
 *   │                       Arabic ayah →  │
 *   │       italic transliteration         │
 *   │       translation paragraph          │
 *   │ [N] ▶ ⭐ ↗  ↘                         │
 *   └──────────────────────────────────────┘
 *
 * Theme-aware: every color comes from `MaterialTheme.colorScheme` so
 * all five themes (Zamrud Islami, Teal & Dusk, Amber Masjid, Indigo
 * Malam, Material You) carry through.
 */
@Composable
fun VerseCard(
    row: VerseRow,
    callbacks: VerseCardCallbacks,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Arabic ayah ────────────────────────────────────────────────
            Text(
                text = row.arabic,
                fontFamily = UthmanicHafsFontFamily,
                fontSize = 26.sp,
                lineHeight = 44.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Transliteration ────────────────────────────────────────────
            if (!row.transliteration.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = row.transliteration,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ── Translation ────────────────────────────────────────────────
            Spacer(Modifier.height(10.dp))
            Text(
                text = row.translation
                    ?: stringResource(R.string.reader_translation_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.translation != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )

            Spacer(Modifier.height(12.dp))
            ActionRow(row = row, callbacks = callbacks)
        }
    }
}

@Composable
private fun ActionRow(
    row: VerseRow,
    callbacks: VerseCardCallbacks,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ayah-number badge — primary-tinted circle
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = row.ayah.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.size(8.dp))

        IconButton(onClick = { callbacks.onPlay(row) }) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.reader_action_play),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { callbacks.onBookmark(row) }) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = stringResource(R.string.reader_action_bookmark),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { callbacks.onShare(row) }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.reader_action_share),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { callbacks.onJumpToMushaf(row) }) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = stringResource(R.string.reader_action_jump_to_mushaf),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Sticky surah header (Arabic + English) above the first verse of a surah. */
@Composable
fun SurahHeaderRow(
    surahNumber: Int,
    surahNameArabic: String,
    surahNameEnglish: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = surahNameArabic,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontFamily = UthmanicHafsFontFamily,
            )
            Text(
                text = "$surahNumber · $surahNameEnglish",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}
