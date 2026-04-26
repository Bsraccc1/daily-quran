package com.quranreader.custom.ui.components.mushaf

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Colour palette used by the mushaf renderer. Derived from the active
 * Material 3 [androidx.compose.material3.ColorScheme] so the page
 * automatically adapts to light, dark, and dynamic-color themes — no
 * hard-coded parchment / dark-sepia presets, no JPG tinting tricks.
 *
 * The renderer paints the bundled transparent-background mushaf
 * page image with [text] via `ColorFilter.tint(SrcIn)`, so the
 * calligraphy ink follows the theme automatically. Light themes
 * produce dark ink on a light page; dark themes produce light ink on
 * a dark page.
 *
 * Use [rememberMushafColors] to obtain an instance inside a Composable.
 */
@Immutable
data class MushafColors(
    /** Page background — fills the whole reading area. */
    val background: Color,
    /** Calligraphy ink colour, applied as a tint to the page image. */
    val text: Color,
    /** Translucent highlight under the currently-selected ayah. */
    val highlight: Color,
    /** Page-number badge text colour (bottom of the page). */
    val pageNumberText: Color,
    /** Page-number badge background. */
    val pageNumberBackground: Color,
)

/**
 * Build a [MushafColors] palette from the current [MaterialTheme]. Re-runs
 * whenever the colour scheme changes (e.g. when the user toggles dark mode
 * or picks a new accent in Settings).
 */
@Composable
fun rememberMushafColors(): MushafColors {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        MushafColors(
            background = cs.background,
            text = cs.onSurface,
            highlight = cs.primary.copy(alpha = 0.18f),
            pageNumberText = cs.onSurfaceVariant,
            pageNumberBackground = cs.surfaceVariant.copy(alpha = 0.85f),
        )
    }
}
