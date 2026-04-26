package com.quranreader.custom.ui.components.animated

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quranreader.custom.ui.theme.Motion

/**
 * Horizontal row of selectable filter chips with animated selection color.
 *
 * Selection color animates over [Motion.standard] (300ms standard easing) when the user
 * picks a different chip, giving a smoother feel than the default M3 instant swap.
 *
 * Usage (single-select):
 * ```kotlin
 * var selected by remember { mutableStateOf("All") }
 * ChipRow(
 *     items = listOf("All", "This Week", "This Month"),
 *     selected = selected,
 *     onSelect = { selected = it },
 *     label = { it }
 * )
 * ```
 *
 * @param items list of chip values
 * @param selected currently-selected value
 * @param onSelect callback fired when user taps a chip
 * @param label maps an item to its visible label string
 * @param contentPadding inner padding; default 16.dp horizontal, 8.dp vertical
 * @param spacing space between chips; default 8.dp
 */
@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    spacing: Int = 8
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing.dp)
    ) {
        items(items) { item ->
            val isSelected = item == selected
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = Motion.standard(),
                label = "ChipRow.containerColor"
            )

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = containerColor,
                    containerColor = containerColor
                )
            )
        }
    }
}

/**
 * Eager (non-lazy) variant of ChipRow for short, fixed-width chip lists.
 */
@Composable
fun <T> EagerChipRow(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    spacing: Int = 8
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = Motion.standard(),
                label = "EagerChipRow.containerColor"
            )

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = containerColor,
                    containerColor = containerColor
                )
            )
        }
    }
}
