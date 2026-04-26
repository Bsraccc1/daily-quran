package com.quranreader.custom.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Future-proof dimension system using simple dp values
 * These values work consistently across all devices and DPIs
 * Android's dp system automatically handles different screen densities
 */
object Dimensions {
    
    // ═══════════════════════════════════════════════════════════════════
    // SPACING & PADDING - Universal values that work on all devices
    // ═══════════════════════════════════════════════════════════════════
    
    val spacingXs: Dp = 4.dp
    val spacingSmall: Dp = 8.dp
    val spacingMedium: Dp = 12.dp
    val spacingDefault: Dp = 16.dp
    val spacingLarge: Dp = 24.dp
    val spacingXl: Dp = 32.dp
    val spacingXxl: Dp = 48.dp
    
    // ═══════════════════════════════════════════════════════════════════
    // ICON SIZES - Material Design recommended sizes
    // ═══════════════════════════════════════════════════════════════════
    
    val iconSizeSmall: Dp = 16.dp
    val iconSizeMedium: Dp = 24.dp
    val iconSizeLarge: Dp = 32.dp
    val iconSizeXl: Dp = 48.dp
    
    // ═══════════════════════════════════════════════════════════════════
    // BUTTON HEIGHTS - Touch-friendly sizes (min 48dp for accessibility)
    // ═══════════════════════════════════════════════════════════════════
    
    val buttonHeightSmall: Dp = 40.dp
    val buttonHeightMedium: Dp = 48.dp
    val buttonHeightLarge: Dp = 56.dp
    
    // ═══════════════════════════════════════════════════════════════════
    // CARD ELEVATION - Material Design elevation levels
    // ═══════════════════════════════════════════════════════════════════
    
    val cardElevationDefault: Dp = 2.dp
    val cardElevationRaised: Dp = 4.dp
    val cardElevationHigh: Dp = 8.dp
    
    // ═══════════════════════════════════════════════════════════════════
    // CORNER RADIUS - Consistent rounded corners
    // ═══════════════════════════════════════════════════════════════════
    
    val cornerRadiusSmall: Dp = 4.dp
    val cornerRadiusMedium: Dp = 8.dp
    val cornerRadiusLarge: Dp = 12.dp
    val cornerRadiusXl: Dp = 16.dp
    val cornerRadiusXxl: Dp = 24.dp
    
    // ═══════════════════════════════════════════════════════════════════
    // SCREEN-SPECIFIC DIMENSIONS
    // ═══════════════════════════════════════════════════════════════════
    
    val homeCircularProgressSize: Dp = 280.dp
    val homeCardMinHeight: Dp = 120.dp
    
    val readingProgressSize: Dp = 280.dp
    val readingButtonHeight: Dp = 56.dp
    
    val navigationCardBadgeSize: Dp = 56.dp
    val navigationCardMinHeight: Dp = 80.dp
    
    val bottomNavHeight: Dp = 80.dp
    val bottomNavIconSize: Dp = 24.dp
    
    val topBarHeight: Dp = 64.dp
    
    val bookmarkCardHeight: Dp = 100.dp
    
    val sessionCardMinHeight: Dp = 120.dp
}
