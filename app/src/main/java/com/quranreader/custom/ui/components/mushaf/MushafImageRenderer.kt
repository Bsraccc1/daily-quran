package com.quranreader.custom.ui.components.mushaf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase.Companion.IMAGE_HEIGHT_PX
import com.quranreader.custom.data.local.ayahinfo.AyahInfoDatabase.Companion.IMAGE_WIDTH_PX
import com.quranreader.custom.data.local.ayahinfo.GlyphEntity
import com.quranreader.custom.data.model.HighlightedAyah
import com.quranreader.custom.ui.viewmodel.MushafImagePageViewModel

/**
 * Renders a single mushaf page from a bundled, transparent-background
 * WebP image (`assets/mushaf_pages/page_<NNN>.webp`). The page image
 * carries only the calligraphy in alpha; the runtime tints it with the
 * theme's text colour through `ColorFilter.tint(SrcIn)` so the page
 * automatically follows light, dark, and dynamic-color themes — no
 * white card flashing in dark mode, no black ink on a black background
 * in light mode.
 *
 *  - **Background**  →  `MaterialTheme.colorScheme.surface`
 *  - **Calligraphy** →  `MaterialTheme.colorScheme.onSurface`
 *  - **Highlight**   →  `colorScheme.primary` at 18% alpha
 *
 * Tap detection uses the bundled `ayahinfo.db` (per-glyph pixel
 * rectangles in the 1024×1656 image space) — a long press maps the
 * touch coordinates back into image pixels and finds the matching
 * sura+ayah, which the parent screen then uses for the existing
 * `AyahActionPopup` / translation flow.
 */
@Composable
fun MushafImageRenderer(
    pageNumber: Int,
    highlightedAyah: HighlightedAyah?,
    onAyahLongPress: (surah: Int, ayah: Int) -> Unit,
    /**
     * Fires when the user taps on the page margin / negative space,
     * i.e. *not* on any glyph. The reader uses this to clear the
     * highlight, which dismisses the slide-down + slide-up panels.
     */
    onSingleTap: () -> Unit = {},
    /**
     * Lightweight tap handler: fires when the user single-taps directly
     * on a glyph rectangle. Used to drive the click-to-highlight flow
     * without triggering the long-press action menu — the highlight
     * itself is what reveals the slide-down and slide-up panels.
     */
    onAyahTap: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") fontScale: Float = 1.0f,
    viewModel: MushafImagePageViewModel = hiltViewModel(key = "mushaf_image_$pageNumber"),
) {
    val colors = rememberMushafColors()
    val glyphs = viewModel.glyphs
    val context = LocalContext.current

    LaunchedEffect(pageNumber) { viewModel.loadPage(pageNumber) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(IMAGE_WIDTH_PX.toFloat() / IMAGE_HEIGHT_PX.toFloat())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            // Image is laid out FillBounds so the draw rectangle exactly
            // matches our BoxWithConstraints — scale factors are simple
            // ratios with no centring offsets to compensate for.
            val scaleX = widthPx / IMAGE_WIDTH_PX
            val scaleY = heightPx / IMAGE_HEIGHT_PX

            // The page image itself. AsyncImage caches the decoded bitmap
            // in Coil's memory cache, so swiping back to a recently-viewed
            // page is instant.
            AsyncImage(
                model = remember(pageNumber) {
                    ImageRequest.Builder(context)
                        .data("file:///android_asset/mushaf_pages/${assetName(pageNumber)}")
                        .crossfade(false)
                        .build()
                },
                contentDescription = "Mushaf page $pageNumber",
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colors.text, BlendMode.SrcIn),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageNumber, glyphs.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                // Hit-test first: a tap that lands on a
                                // glyph means "highlight this verse",
                                // a tap on margin / negative space falls
                                // through to the existing info-panel
                                // toggle. This gives users the
                                // click-to-highlight behaviour without
                                // breaking the established chrome
                                // toggle UX.
                                val hit = hitTest(glyphs, offset, scaleX, scaleY)
                                if (hit != null) {
                                    onAyahTap(hit.suraNumber, hit.ayahNumber)
                                } else {
                                    onSingleTap()
                                }
                            },
                            onLongPress = { offset ->
                                val hit = hitTest(glyphs, offset, scaleX, scaleY)
                                if (hit != null) {
                                    onAyahLongPress(hit.suraNumber, hit.ayahNumber)
                                }
                            },
                        )
                    },
            )

            // Translucent highlight under the currently-selected ayah.
            // We group glyphs per line so a multi-line ayah gets one
            // rounded rectangle per line instead of a per-glyph box.
            if (highlightedAyah != null && glyphs.isNotEmpty()) {
                val highlight = remember(highlightedAyah, glyphs) {
                    glyphs.filter {
                        it.suraNumber == highlightedAyah.surah &&
                                it.ayahNumber == highlightedAyah.ayah
                    }
                }
                if (highlight.isNotEmpty()) {
                    val highlightColor = colors.highlight
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        highlight.groupBy { it.lineNumber }.values.forEach { lineGlyphs ->
                            val minX = lineGlyphs.minOf { it.minX } * scaleX
                            val maxX = lineGlyphs.maxOf { it.maxX } * scaleX
                            val minY = lineGlyphs.minOf { it.minY } * scaleY
                            val maxY = lineGlyphs.maxOf { it.maxY } * scaleY
                            // tiny padding around the box so the highlight
                            // doesn't touch the calligraphy edges
                            val pad = 2f
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(minX - pad, minY - pad),
                                size = Size(
                                    width = (maxX - minX) + pad * 2,
                                    height = (maxY - minY) + pad * 2,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Page-number badge at the bottom centre — purely chrome, kept
        // identical to the previous text-based renderer so the rest of
        // the UI (info panel, audio bar) doesn't need to know we
        // changed how pages are drawn.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = colors.pageNumberBackground,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = "صفحة ${formatEasternArabic(pageNumber)}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.pageNumberText,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Find the glyph whose pixel rect contains the given touch point.
 * Returns the first match — glyph rectangles never overlap, so this is
 * deterministic.
 */
private fun hitTest(
    glyphs: List<GlyphEntity>,
    offset: Offset,
    scaleX: Float,
    scaleY: Float,
): GlyphEntity? {
    if (glyphs.isEmpty()) return null
    val ix = offset.x / scaleX
    val iy = offset.y / scaleY
    return glyphs.firstOrNull {
        ix >= it.minX && ix <= it.maxX && iy >= it.minY && iy <= it.maxY
    }
}

/**
 * Build the asset filename for a given mushaf page (1-based). All 604
 * page assets are zero-padded to three digits to match the encoder's
 * output (`build_mushaf_pages.py`).
 */
private fun assetName(pageNumber: Int): String =
    "page_${pageNumber.toString().padStart(3, '0')}.webp"

/** Convert an integer to its Eastern Arabic digit representation. */
private fun formatEasternArabic(n: Int): String {
    val digits = "٠١٢٣٤٥٦٧٨٩"
    return n.toString().map { digits[it.digitToInt()] }.joinToString("")
}
