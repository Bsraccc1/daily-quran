package com.quranreader.custom.ui.components.animated

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quranreader.custom.ui.theme.QuranReaderTheme

@Preview(showBackground = true)
@Composable
private fun AnimatedCardPreview() {
    QuranReaderTheme {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            AnimatedCard(onClick = { /* preview */ }) {
                Text("Tap me — Surah Al-Fatihah")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChipRowPreview() {
    QuranReaderTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var selected by remember { mutableStateOf("All") }
            ChipRow(
                items = listOf("All", "This Week", "This Month", "By Surah"),
                selected = selected,
                onSelect = { selected = it },
                label = { it }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnimatedCounterRingPreview() {
    QuranReaderTheme {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedCounterRing(completed = 0, target = 3)
                AnimatedCounterRing(completed = 1, target = 3)
                AnimatedCounterRing(completed = 2, target = 3)
                AnimatedCounterRing(completed = 3, target = 3)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpandableSectionPreview() {
    QuranReaderTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpandableSection(
                    title = "Audio",
                    icon = Icons.Default.PlayArrow,
                    initiallyExpanded = false
                ) {
                    Text(
                        text = "Reciter selection, default volume, audio cache size",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                ExpandableSection(
                    title = "Display",
                    initiallyExpanded = true
                ) {
                    Text(
                        text = "Theme, language, font size",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
