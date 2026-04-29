package com.quranreader.custom.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.ui.components.CircularReadingProgress
import com.quranreader.custom.ui.components.session.CreateSessionDialog
import com.quranreader.custom.ui.screens.search.AyahSearchDialog
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.SessionViewModel
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Dashboard Screen - Shows circular progress and session controls
 * Syncs with Session tab for unified session management
 */
@Composable
fun DashboardScreen(
    /**
     * Invoked when the search dialog resolves a (surah, ayah) pair to
     * a mushaf page. The host (NavGraph) jumps to the reader with the
     * verse pre-highlighted via the bundled `ayahinfo.db`.
     */
    onNavigateToAyah: (page: Int, surah: Int, ayah: Int) -> Unit = { _, _, _ -> },
    /**
     * Open the reader and auto-start a session. Receives the page
     * to land on, the session's anchor `startPage`, and the
     * `targetPages`. These are forwarded as nav arguments so the
     * reader's limit math is anchored to the user's chosen values
     * (no race with the legacy session DataStore Flow).
     */
    onNavigateToMushafWithSession: (page: Int, startPage: Int, targetPages: Int) -> Unit = { _, _, _ -> },
    readingViewModel: ReadingViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentPage by readingViewModel.currentPage.collectAsState()
    val sessions by sessionViewModel.sessions.collectAsState()
    val activeSessionId by sessionViewModel.activeSessionId.collectAsState()
    
    // Find active session
    val activeSession = sessions.find { it.id == activeSessionId }
    
    // Progress based on active session (if exists), otherwise overall Quran progress
    val progress = if (activeSession != null && activeSession.targetPages > 0) {
        activeSession.pagesRead.toFloat() / activeSession.targetPages.toFloat()
    } else {
        currentPage.toFloat() / 604f
    }
    
    val displayPage = if (activeSession != null) {
        activeSession.pagesRead
    } else {
        currentPage
    }
    
    val totalPages = if (activeSession != null) {
        activeSession.targetPages
    } else {
        604
    }
    
    val currentSurah = remember(currentPage) {
        QuranInfo.getSurahEnglishName(
            (1..114).lastOrNull {
                QuranInfo.getStartPage(it) <= currentPage
            } ?: 1
        )
    }
    val currentAyah = 1
    
    // Dialog states
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    // The Find-Verse dialog replaces the old fullscreen Search route.
    // Hosted here because Dashboard owns the only entry point.
    var showSearchDialog by remember { mutableStateOf(false) }

    // ── Backdrop ─────────────────────────────────────────────────────────────
    // Two layers:
    //  1. A vertical gradient driven by the active
    //     `colorScheme.primaryContainer → background → secondaryContainer`,
    //     so the dashboard re-tints whenever the user switches between
    //     Zamrud / Teal / Amber / Indigo / Material You. On top of the
    //     gradient we tile a low-alpha **Rub el Hizb** (eight-pointed
    //     star) pattern — a fully symmetric Islamic motif — so the
    //     translucent panels have a textured backdrop.
    //  2. The "foreground" Column with the header, panels, and
    //     bottom hints.
    //
    // Frosted-glass (Haze) was retired in v9.0.0 in favour of plain
    // translucent surfaces — they render identically on every API
    // level and removed a moderately heavy GPU dependency.
    val cs = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1 — backdrop. The vertical gradient pulls its stops
        // from the active palette so the same composable re-tints
        // across all 5 themes without per-theme branching.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            cs.primaryContainer.copy(alpha = 0.55f),
                            cs.background,
                            cs.secondaryContainer.copy(alpha = 0.40f),
                        )
                    )
                ),
        ) {
            // Symmetric Islamic geometric pattern — tiled eight-pointed
            // stars (Rub el Hizb) at low alpha. Stroke colour follows
            // `cs.primary`, accent dots follow `cs.tertiary`, both at
            // alphas low enough to keep foreground chrome legible while
            // still giving the translucent panels a textured backdrop.
            IslamicPatternOverlay(
                lineColor = cs.primary.copy(alpha = 0.14f),
                accentColor = cs.tertiary.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 2 — foreground.
        // The whole stack scrolls so the bottom chip stays reachable
        // even on small screens where Continue + Start + circle would
        // otherwise overflow the viewport. We avoid weighted spacers
        // (which collapse to 0 dp inside a scrollable column anyway)
        // and instead use fixed spacing — that's also what gives us
        // a stable "Mac-like" rhythm regardless of session state.
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: "Daily Quran" + Search icon (kept transparent on
            // top of the gradient — no glass needed for a tiny title row).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(com.quranreader.custom.R.string.reading_daily_quran),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                )
                IconButton(onClick = { showSearchDialog = true }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = context.getString(com.quranreader.custom.R.string.nav_search),
                        tint = cs.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Circular progress, centred inside a circular glass plate
            // so the Mushaf-style ring stands out from the gradient.
            // Glass plate is 232 dp — tight wrap around the inner
            // 210 dp progress ring so the dashboard stays compact
            // enough to fit the bottom chip on standard 5.5"-ish
            // screens without scrolling.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassPanel(
                    shape = CircleShape,
                    modifier = Modifier
                        .size(232.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularReadingProgress(
                            progress = progress,
                            currentPage = displayPage,
                            totalPages = totalPages,
                            currentSurah = if (activeSession != null) activeSession.name else currentSurah,
                            currentAyah = if (activeSession != null) 0 else currentAyah,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Session controls — wrapped in a single glass card so the
            // two buttons read as one cohesive panel even when only the
            // "Start New" button is showing (no active session).
            GlassPanel(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (activeSession != null && !activeSession.isCompleted) {
                        Button(
                            onClick = {
                                sessionViewModel.activateSession(activeSession)
                                // Resume on the *last page actually read*
                                // (`startPage + pagesRead - 1`), not the
                                // page after — so a 10-page session that
                                // finished page 10 resumes on page 10.
                                // Falls back to the anchor page when the
                                // session is brand new (pagesRead == 0).
                                val resumePage = if (activeSession.pagesRead > 0) {
                                    (activeSession.startPage + activeSession.pagesRead - 1)
                                        .coerceIn(1, 604)
                                } else {
                                    activeSession.startPage.coerceIn(1, 604)
                                }
                                onNavigateToMushafWithSession(
                                    resumePage,
                                    activeSession.startPage,
                                    activeSession.targetPages,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.primary,
                                contentColor = cs.onPrimary,
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    context.getString(com.quranreader.custom.R.string.dashboard_continue_session),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    context.getString(
                                        com.quranreader.custom.R.string.dashboard_session_progress,
                                        activeSession.name,
                                        activeSession.pagesRead,
                                        activeSession.targetPages,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    // "Start New Session" — theme-aware tonal button so
                    // it stays legible inside the frosted panel without
                    // fighting the primary action above.
                    FilledTonalButton(
                        onClick = { showCreateSessionDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = cs.onSurface,
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            context.getString(com.quranreader.custom.R.string.dashboard_start_new_session),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Hint text — readable directly on the gradient (no glass).
            Text(
                text = if (activeSession != null) {
                    context.getString(com.quranreader.custom.R.string.dashboard_continue_or_new, activeSession.name)
                } else {
                    context.getString(com.quranreader.custom.R.string.dashboard_create_new_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onBackground.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Bottom progress chip — small glass pill so it doesn't
            // disappear into the gradient on light themes. We use
            // fixed padding (not a weighted spacer) so the chip can
            // never end up squashed against the bottom navigation
            // when the buttons panel grows tall on small screens.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 4.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassPanel(
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = if (activeSession != null) {
                            context.getString(
                                com.quranreader.custom.R.string.dashboard_session_progress_label,
                                activeSession.pagesRead,
                                activeSession.targetPages,
                            )
                        } else {
                            context.getString(
                                com.quranreader.custom.R.string.dashboard_overall_progress,
                                currentPage,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    // Create Session Dialog — unified component shared with the
    // Session tab so the same widget creates the session no matter
    // where the user starts. Dashboard's variant uses "Create &
    // Start" as the confirm label (we navigate into the reader
    // immediately) while the Session tab uses "Create".
    if (showCreateSessionDialog) {
        CreateSessionDialog(
            initialPage = currentPage,
            sessionCount = sessions.size,
            confirmLabel = context.getString(com.quranreader.custom.R.string.dashboard_create_start),
            onDismiss = { showCreateSessionDialog = false },
            onConfirm = { name, startPage, targetPages ->
                sessionViewModel.createSession(
                    name = name,
                    startPage = startPage,
                    targetPages = targetPages,
                )
                showCreateSessionDialog = false
                onNavigateToMushafWithSession(startPage, startPage, targetPages)
            },
        )
    }

    // ── Find-Verse popup dialog ──────────────────────────────────────
    // Mounted at the bottom so its window sits above the rest of the
    // dashboard chrome. The dialog is responsible for its own sizing
    // (DPI-agnostic via dp + percentage of available window) — we just
    // wire dismiss and the resolved (page, surah, ayah) callback.
    if (showSearchDialog) {
        AyahSearchDialog(
            onDismiss = { showSearchDialog = false },
            onResult = { page, surah, ayah ->
                onNavigateToAyah(page, surah, ayah)
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Translucent panel helpers — kept private to the dashboard because they
// encode tuning specific to this screen's layout. The reader has its
// own `PanelSurface` with different sizing / shadow tokens.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Translucent panel surface used across the dashboard. v9.0 dropped
 * the Haze-backed frosted-glass implementation in favour of plain
 * Material 3 surfaces tinted at 75% alpha — they render identically
 * on every API level (no Android 12+ `RenderEffect` requirement) and
 * stop the dashboard chasing GPU work on every recomposition.
 *
 * The panel renders as:
 *  - Soft drop shadow so the surface appears to float over the gradient.
 *  - Clipped to [shape].
 *  - Theme-aware fill (`surface` at 75% alpha) so the gradient + Rub
 *    el Hizb pattern still bleed through subtly behind the chrome.
 *  - Hairline border at 12% on-surface for a clean edge.
 */
@Composable
private fun GlassPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tint = cs.surface.copy(alpha = 0.75f)
    val borderColor = cs.onSurface.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(color = tint, shape = shape)
            .border(
                width = 0.6.dp,
                color = borderColor,
                shape = shape,
            ),
    ) {
        content()
    }
}

/**
 * Symmetric Islamic geometric pattern used as the dashboard backdrop.
 *
 * Tiles the canvas with **Rub el Hizb** (خاتم سليمان) — the
 * eight-pointed star formed by two overlapping squares (one in
 * diamond orientation, one axis-aligned, both inscribed in the same
 * circle). The motif is the same one that traditionally marks every
 * quarter-juz division in a printed mushaf, and is fully radially
 * symmetric — replacing the previous trio of asymmetric blurred orbs
 * with a composition that feels deliberate from any aspect ratio.
 *
 * Colours are taken from `MaterialTheme.colorScheme` so the pattern
 * re-tints with the active palette (Zamrud / Teal / Amber / Indigo /
 * Material You) without any per-theme branching.
 *
 * The grid is centred on the canvas so the pattern stays visually
 * balanced regardless of screen size, and we draw one extra row /
 * column of stars off-screen so partial tiles never reveal a hard
 * cropping edge against the gradient.
 */
@Composable
private fun IslamicPatternOverlay(
    lineColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val tilePx = 96.dp.toPx()
        val starRadius = tilePx * 0.34f
        val strokePx = 1.dp.toPx()
        val accentRadius = 1.6.dp.toPx()
        val cols = ceil(size.width / tilePx).toInt() + 2
        val rows = ceil(size.height / tilePx).toInt() + 2
        // Centre the grid so the pattern is symmetric about the canvas.
        val originX = (size.width - (cols - 1) * tilePx) / 2f
        val originY = (size.height - (rows - 1) * tilePx) / 2f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cx = originX + col * tilePx
                val cy = originY + row * tilePx
                drawRubElHizb(cx, cy, starRadius, strokePx, lineColor)
                drawCircle(
                    color = accentColor,
                    radius = accentRadius,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/**
 * Draws one Rub el Hizb centred at ([cx], [cy]). Composed of two
 * squares both inscribed in a circle of radius [r]: one rotated 45°
 * (diamond) and one axis-aligned. Their vertices interleave at every
 * 45° around the centre, producing the classic eight-pointed star
 * with full eight-fold rotational symmetry.
 */
private fun DrawScope.drawRubElHizb(
    cx: Float,
    cy: Float,
    r: Float,
    strokeWidth: Float,
    color: Color,
) {
    val a = r / sqrt(2f)
    val stroke = Stroke(width = strokeWidth)

    val diamond = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r, cy)
        close()
    }
    val square = Path().apply {
        moveTo(cx - a, cy - a)
        lineTo(cx + a, cy - a)
        lineTo(cx + a, cy + a)
        lineTo(cx - a, cy + a)
        close()
    }

    drawPath(diamond, color, style = stroke)
    drawPath(square, color, style = stroke)
}
