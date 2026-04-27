package com.quranreader.custom.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.ui.components.CircularReadingProgress
import com.quranreader.custom.ui.screens.search.AyahSearchDialog
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.ui.viewmodel.SessionViewModel

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
    onNavigateToMushafWithSession: (Int) -> Unit = {},
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
            (1..114).firstOrNull { 
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header: "Daily Quran" + Search icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(com.quranreader.custom.R.string.reading_daily_quran),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { showSearchDialog = true }) {
                Icon(Icons.Outlined.Search, contentDescription = context.getString(com.quranreader.custom.R.string.nav_search))
            }
        }

        Spacer(Modifier.weight(0.2f))

        // Circular progress indicator (shows active session progress)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularReadingProgress(
                progress = progress,
                currentPage = displayPage,
                totalPages = totalPages,
                currentSurah = if (activeSession != null) activeSession.name else currentSurah,
                currentAyah = if (activeSession != null) 0 else currentAyah
            )
        }

        Spacer(Modifier.height(24.dp))

        // Session Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeSession != null && !activeSession.isCompleted) {
                // Continue Session Button
                Button(
                    onClick = {
                        sessionViewModel.activateSession(activeSession)
                        onNavigateToMushafWithSession(activeSession.startPage + activeSession.pagesRead)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            context.getString(com.quranreader.custom.R.string.dashboard_continue_session),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            context.getString(com.quranreader.custom.R.string.dashboard_session_progress, 
                                activeSession.name, activeSession.pagesRead, activeSession.targetPages),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Start New Session Button
            OutlinedButton(
                onClick = { showCreateSessionDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    context.getString(com.quranreader.custom.R.string.dashboard_start_new_session),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(Modifier.weight(0.5f))

        // Info text
        Text(
            text = if (activeSession != null) {
                context.getString(com.quranreader.custom.R.string.dashboard_continue_or_new, activeSession.name)
            } else {
                context.getString(com.quranreader.custom.R.string.dashboard_create_new_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        )

        Spacer(Modifier.weight(0.3f))

        // Bottom hint - shows session progress or overall progress
        Text(
            text = if (activeSession != null) {
                context.getString(com.quranreader.custom.R.string.dashboard_session_progress_label, 
                    activeSession.pagesRead, activeSession.targetPages)
            } else {
                context.getString(com.quranreader.custom.R.string.dashboard_overall_progress, currentPage)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
    }

    // Create Session Dialog
    if (showCreateSessionDialog) {
        var nameInput by remember { mutableStateOf(context.getString(com.quranreader.custom.R.string.session_new) + " ${sessions.size + 1}") }
        var targetInput by remember { mutableStateOf("10") }
        var startFromCurrent by remember { mutableStateOf(true) }
        var customPageInput by remember { mutableStateOf(currentPage.toString()) }

        AlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            title = { Text(context.getString(com.quranreader.custom.R.string.dashboard_new_session_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(context.getString(com.quranreader.custom.R.string.dashboard_session_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it.filter { c -> c.isDigit() } },
                        label = { Text(context.getString(com.quranreader.custom.R.string.dashboard_target_pages)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = startFromCurrent,
                            onCheckedChange = { startFromCurrent = it }
                        )
                        Text(context.getString(com.quranreader.custom.R.string.dashboard_start_from_current, currentPage))
                    }
                    if (!startFromCurrent) {
                        OutlinedTextField(
                            value = customPageInput,
                            onValueChange = { customPageInput = it.filter { c -> c.isDigit() } },
                            label = { Text(context.getString(com.quranreader.custom.R.string.dashboard_start_page)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = targetInput.toIntOrNull()?.coerceIn(1, 604) ?: 10
                    val startPage = if (startFromCurrent) currentPage
                    else customPageInput.toIntOrNull()?.coerceIn(1, 604) ?: currentPage
                    
                    // Create session via SessionViewModel (syncs to Session tab)
                    sessionViewModel.createSession(
                        name = nameInput.ifBlank { context.getString(com.quranreader.custom.R.string.session_new) + " ${sessions.size + 1}" },
                        startPage = startPage,
                        targetPages = target
                    )
                    
                    showCreateSessionDialog = false
                    
                    // Navigate to mushaf with new session
                    onNavigateToMushafWithSession(startPage)
                }) { Text(context.getString(com.quranreader.custom.R.string.dashboard_create_start)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSessionDialog = false }) { 
                    Text(context.getString(com.quranreader.custom.R.string.common_cancel)) 
                }
            }
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
