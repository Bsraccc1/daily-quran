package com.quranreader.custom.ui.screens.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.data.preferences.ReadingSession
import com.quranreader.custom.ui.viewmodel.SessionViewModel

/**
 * Session Management Screen — NO TopAppBar
 * All navigation handled via bottom bar.
 */
@Composable
fun SessionManagementScreen(
    /**
     * Invoked when the user activates a session. Receives:
     *  - `page`        — the page to land on (current progress, not just startPage),
     *  - `startPage`   — the session's anchor page (used by the reader's
     *                     limit math; never the same as `page` once the
     *                     user has read at least one page),
     *  - `targetPages` — the total pages the session should cover.
     *
     * The host wires these into the reader's nav route so the
     * auto-session uses the exact values the user picked instead of
     * defaulting to `newSessionLimit` while the legacy DataStore Flow
     * is still settling.
     */
    onStartReading: (page: Int, startPage: Int, targetPages: Int) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val lastPage by viewModel.lastPage.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ReadingSession?>(null) }
    var showExtendDialog by remember { mutableStateOf<ReadingSession?>(null) }
    var showRenameDialog by remember { mutableStateOf<ReadingSession?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        "No sessions yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Session")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        isActive = session.id == activeSessionId,
                        onActivate = {
                            viewModel.activateSession(session)
                            // Land on the user's actual progress within
                            // the session — `startPage + pagesRead` —
                            // not the anchor page itself, so re-opening
                            // an in-progress session resumes where the
                            // user left off. The limit math still keys
                            // off the anchor page passed separately.
                            val resumePage = (session.startPage + session.pagesRead)
                                .coerceIn(1, 604)
                            onStartReading(resumePage, session.startPage, session.targetPages)
                        },
                        onRename = { showRenameDialog = session },
                        onExtend = { showExtendDialog = session },
                        onDelete = { showDeleteDialog = session },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // FAB for creating new session
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "New Session")
        }
    }

    // ── Create Session Dialog ─────────────────────────────────────────────────
    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("Session ${sessions.size + 1}") }
        var targetInput by remember { mutableStateOf("10") }
        var startFromCurrent by remember { mutableStateOf(true) }
        var customPageInput by remember { mutableStateOf(lastPage.toString()) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Reading Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Session Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Target Pages") },
                        supportingText = { Text("Total pages to read in this session") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { startFromCurrent = !startFromCurrent }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = startFromCurrent,
                            onCheckedChange = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Start from current page (Page $lastPage)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!startFromCurrent) {
                        OutlinedTextField(
                            value = customPageInput,
                            onValueChange = { customPageInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Start Page (1-604)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = targetInput.toIntOrNull()?.coerceIn(1, 604) ?: 10
                    val startPage = if (startFromCurrent) lastPage
                    else customPageInput.toIntOrNull()?.coerceIn(1, 604) ?: lastPage
                    
                    viewModel.createSession(
                        name = nameInput.ifBlank { "Session ${sessions.size + 1}" },
                        startPage = startPage,
                        targetPages = target
                    )
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Rename Session Dialog ─────────────────────────────────────────────────
    showRenameDialog?.let { session ->
        var nameInput by remember { mutableStateOf(session.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.updateSession(session.copy(name = nameInput.trim()))
                        }
                        showRenameDialog = null
                    },
                    enabled = nameInput.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }

    // ── Extend Session Dialog ─────────────────────────────────────────────────
    showExtendDialog?.let { session ->
        var extendInput by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { showExtendDialog = null },
            title = { Text("Extend \"${session.name}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current target: ${session.targetPages} pages")
                    OutlinedTextField(
                        value = extendInput,
                        onValueChange = { extendInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Add pages") },
                        singleLine = true
                    )
                    val add = extendInput.toIntOrNull() ?: 0
                    Text(
                        "New target: ${session.targetPages + add} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val add = extendInput.toIntOrNull() ?: 5
                    viewModel.extendSession(session.id, add)
                    showExtendDialog = null
                }) { Text("Extend") }
            },
            dismissButton = {
                TextButton(onClick = { showExtendDialog = null }) { Text("Cancel") }
            }
        )
    }

    // ── Delete Confirmation ───────────────────────────────────────────────────
    showDeleteDialog?.let { session ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"${session.name}\"?") },
            text = { Text("This session will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    showDeleteDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SessionCard(
    session: ReadingSession,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (session.targetPages > 0)
        (session.pagesRead.toFloat() / session.targetPages).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                session.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                isActive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(if (isActive) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (session.isCompleted) Icons.Default.CheckCircle else Icons.Default.Timer,
                        contentDescription = null,
                        tint = when {
                            session.isCompleted -> MaterialTheme.colorScheme.primary
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        session.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row {
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Rename", modifier = Modifier.size(18.dp))
                    }
                    if (!session.isCompleted) {
                        IconButton(onClick = onExtend, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, "Extend", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete, "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoChip("Start", "Page ${session.startPage}")
                InfoChip("Target", "${session.targetPages} pages")
                InfoChip("Read", "${session.pagesRead} pages")
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "${(progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (!session.isCompleted) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isActive) "Continue Reading" else "Start Reading")
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
