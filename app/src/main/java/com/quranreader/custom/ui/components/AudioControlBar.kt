package com.quranreader.custom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Audio Control Bar with Range Playback and Repeat functionality
 * Appears at bottom of reading screen
 */
@Composable
fun AudioControlBar(
    isVisible: Boolean,
    isPlaying: Boolean,
    currentAyah: Int?,
    fromAyah: Int,
    toAyah: Int,
    repeatCount: Int,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRangeChange: (Int, Int) -> Unit,
    onRepeatChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRangeDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Current ayah indicator
                if (currentAyah != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Playing Ayah $currentAyah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "Range: $fromAyah-$toAyah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Control buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Range selector button
                    OutlinedButton(
                        onClick = { showRangeDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("$fromAyah-$toAyah", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Play/Pause button
                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Repeat button
                    OutlinedButton(
                        onClick = { showRepeatDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (repeatCount) {
                                0 -> "∞"
                                else -> "${repeatCount}x"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Stop button
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Range Selection Dialog
    if (showRangeDialog) {
        AudioRangeDialog(
            fromAyah = fromAyah,
            toAyah = toAyah,
            maxAyah = 286, // TODO: Get from current surah
            onConfirm = { from, to ->
                onRangeChange(from, to)
                showRangeDialog = false
            },
            onDismiss = { showRangeDialog = false }
        )
    }

    // Repeat Count Dialog
    if (showRepeatDialog) {
        RepeatCountDialog(
            currentRepeat = repeatCount,
            onSelect = { count ->
                onRepeatChange(count)
                showRepeatDialog = false
            },
            onDismiss = { showRepeatDialog = false }
        )
    }
}

/**
 * Dialog for selecting ayah range (From Ayah X to Ayah Y)
 */
@Composable
private fun AudioRangeDialog(
    fromAyah: Int,
    toAyah: Int,
    maxAyah: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var from by remember { mutableStateOf(fromAyah) }
    var to by remember { mutableStateOf(toAyah) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Select ayah range to play",
                    style = MaterialTheme.typography.bodyMedium
                )

                // From Ayah
                Column {
                    Text(
                        "From Ayah",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = from.toFloat(),
                        onValueChange = {
                            from = it.toInt()
                            isError = from > to
                        },
                        valueRange = 1f..maxAyah.toFloat(),
                        steps = maxAyah - 2
                    )
                    Text(
                        "Ayah $from",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // To Ayah
                Column {
                    Text(
                        "To Ayah",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = to.toFloat(),
                        onValueChange = {
                            to = it.toInt()
                            isError = from > to
                        },
                        valueRange = 1f..maxAyah.toFloat(),
                        steps = maxAyah - 2
                    )
                    Text(
                        "Ayah $to",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (isError) {
                    Text(
                        "From Ayah must be ≤ To Ayah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(from, to) },
                enabled = !isError
            ) {
                Text("Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for selecting repeat count (1x, 2x, 3x, 5x, ∞)
 */
@Composable
private fun RepeatCountDialog(
    currentRepeat: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val repeatOptions = listOf(
        1 to "1x",
        2 to "2x",
        3 to "3x",
        5 to "5x",
        0 to "∞ Infinite"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat Count") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "How many times to repeat each ayah?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))

                repeatOptions.forEach { (count, label) ->
                    val isSelected = count == currentRepeat
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(count) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected)
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
