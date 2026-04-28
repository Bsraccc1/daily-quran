package com.quranreader.custom.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for the panel/dialog width caps the reader
 * uses across phones and tablets.
 *
 * The reader's slide-down + slide-up panels, the navigate dialog, the
 * ayah-search dialog, the translation slide-up, and the translation
 * editions catalogue all share the same sizing rule so the chrome
 * stays visually consistent regardless of which one the user opens.
 *
 *  - **Compact** (`< 600 dp`): the panel takes 92% of the available
 *    width, capped at 480 dp. Matches the original phone layout —
 *    most modern handsets land in this bucket.
 *  - **Medium** (`600..839 dp`): inner / outer foldables, 7" tablets
 *    in portrait. Caps at 560 dp so the panel still feels like a
 *    floating element rather than a full-bleed bottom sheet.
 *  - **Expanded** (`>= 840 dp`): 10" tablets, foldables in landscape,
 *    Chromebooks. Caps at 640 dp — wider than a phone-sized panel so
 *    the icons and chips have breathing room, but still narrow enough
 *    that the eye doesn't have to scan across the whole screen.
 *
 *  ```
 *  BoxWithConstraints(contentAlignment = Alignment.TopCenter) {
 *      Surface(
 *          modifier = Modifier
 *              .widthIn(
 *                  min = MIN_PANEL_WIDTH.coerceAtMost(maxWidth),
 *                  max = responsivePanelMaxWidth(maxWidth),
 *              )
 *              .fillMaxWidth(),
 *      ) { … }
 *  }
 *  ```
 */
internal fun responsivePanelMaxWidth(available: Dp): Dp = when {
    available >= 840.dp -> 640.dp
    available >= 600.dp -> 560.dp
    else -> (available * 0.92f).coerceAtMost(480.dp)
}

/**
 * Floor for any responsive panel — never narrower than this even on
 * unusually small windows (e.g. split-screen on a small phone).
 */
internal val MIN_PANEL_WIDTH: Dp = 280.dp
