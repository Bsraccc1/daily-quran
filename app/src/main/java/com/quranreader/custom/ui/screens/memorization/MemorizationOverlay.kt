package com.quranreader.custom.ui.screens.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.sync.AyahKey
import com.quranreader.custom.ui.components.animated.AnimatedCounterRing
import com.quranreader.custom.ui.components.animated.ChipRow
import com.quranreader.custom.ui.viewmodel.MemorizationViewModel

/**
 * Bottom-sheet overlay that hosts the memorization (hifz) controls on top of the
 * mushaf reader screen. Reading screen circular UI stays visible behind the sheet.
 *
 * UI elements:
 * - Current ayah label + AnimatedCounterRing (target -> 0)
 * - Repeat-target ChipRow (3 / 5 / 10 / 20)
 * - Auto-advance switch
 * - Play / pause / next / stop transport row
 *
 * @param initialAyah ayah to start memorizing (typically the user's current ayah)
 * @param onDismiss invoked when the user closes the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationOverlay(
    initialAyah: AyahKey,
    onDismiss: () -> Unit,
    viewModel: MemorizationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val targets = listOf(3, 5, 10, 20)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Memorize",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Current ayah label
            val activeAyah = state.currentAyah.takeIf { state.sessionId != null } ?: initialAyah
            val surahName = QuranInfo.getSurahEnglishName(activeAyah.surah)
            Text(
                text = "$surahName ${activeAyah.surah}:${activeAyah.ayah}",
                style = MaterialTheme.typography.titleMedium
            )

            // Counter ring
            AnimatedCounterRing(
                completed = state.repeatsCompleted,
                target = state.repeatTarget,
                size = 120.dp,
                strokeWidth = 10.dp
            )

            // Repeat target picker
            Text("Repeat each ayah", style = MaterialTheme.typography.labelLarge)
            ChipRow(
                items = targets,
                selected = state.repeatTarget,
                onSelect = { newTarget ->
                    if (state.sessionId == null) {
                        viewModel.start(activeAyah.surah, activeAyah.ayah, newTarget, state.autoAdvance)
                    } else {
                        // mid-session change: restart with new target
                        viewModel.start(activeAyah.surah, activeAyah.ayah, newTarget, state.autoAdvance)
                    }
                },
                label = { "${it}x" }
            )

            // Auto-advance toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-advance to next ayah", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.autoAdvance,
                    onCheckedChange = { newValue ->
                        // restart if mid-session w/ new flag
                        viewModel.start(
                            activeAyah.surah,
                            activeAyah.ayah,
                            state.repeatTarget,
                            newValue
                        )
                    }
                )
            }

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (state.sessionId == null) {
                        viewModel.start(activeAyah.surah, activeAyah.ayah, state.repeatTarget, state.autoAdvance)
                    } else if (state.isPlaying) {
                        viewModel.pause()
                    } else {
                        viewModel.resume()
                    }
                }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { viewModel.nextAyah() }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next ayah",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = {
                    viewModel.complete()
                    onDismiss()
                }) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop session",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
