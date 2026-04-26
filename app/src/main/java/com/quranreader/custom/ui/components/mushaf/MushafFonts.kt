package com.quranreader.custom.ui.components.mushaf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.quranreader.custom.R

/**
 * Madinah-mushaf KFGQPC Hafs Uthmanic Script font (`uthmanic_hafs.otf` in
 * `res/font/`). This is the font that gives the bundled-text mushaf its
 * authentic printed-Quran appearance.
 */
val UthmanicHafsFontFamily: FontFamily = FontFamily(
    Font(R.font.uthmanic_hafs, weight = FontWeight.Normal),
)

/** Decorative font used for surah-name ornaments. */
val QuranTitlesFontFamily: FontFamily = FontFamily(
    Font(R.font.quran_titles, weight = FontWeight.Normal),
)

/** Naskh fallback for translations / generic Arabic UI text. */
val UthmanNaskhFontFamily: FontFamily = FontFamily(
    Font(R.font.uthman_naskh, weight = FontWeight.Normal),
)

@Composable
fun rememberMushafFontFamily(): FontFamily = remember { UthmanicHafsFontFamily }
