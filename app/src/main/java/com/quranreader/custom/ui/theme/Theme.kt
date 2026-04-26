package com.quranreader.custom.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * QuranReaderTheme — supports 4 static palettes (light+dark) plus Material You.
 *
 * @param themeId one of: zamrud_light, zamrud_dark, teal_light, teal_dark,
 *                amber_light, amber_dark, indigo_light, indigo_dark, material_you
 */
@Composable
fun QuranReaderTheme(
    themeId: String = "zamrud_light",
    content: @Composable () -> Unit
) {
    val colorScheme = resolveColorScheme(themeId)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
private fun resolveColorScheme(themeId: String): ColorScheme {
    // Material You — guarded by API 31+
    if (themeId == "material_you" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    }

    // Static palettes
    return when (themeId) {
        "zamrud_light"  -> ZamrudLightColorScheme
        "zamrud_dark"   -> ZamrudDarkColorScheme
        "teal_light"    -> TealLightColorScheme
        "teal_dark"     -> TealDarkColorScheme
        "amber_light"   -> AmberLightColorScheme
        "amber_dark"    -> AmberDarkColorScheme
        "indigo_light"  -> IndigoLightColorScheme
        "indigo_dark"   -> IndigoDarkColorScheme
        // Fallback to Zamrud Light if themeId is unknown
        else            -> ZamrudLightColorScheme
    }
}
