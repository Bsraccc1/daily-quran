package com.quranreader.custom.ui.components.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quranreader.custom.R
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.QuranNavigationData

/**
 * Anchor strategy used by the session-creation dialog.
 *
 * - **PAGE**: classic "start at page N, run for M pages." User picks
 *   the start page (or accepts the current page) and the page count.
 * - **JUZ**: pick a juz (1..30) and the dialog auto-derives the start
 *   page and page count from [QuranNavigationData.juzPageBounds] —
 *   one decision instead of two.
 */
enum class SessionBasis(val label: String) {
    PAGE("Page"),
    JUZ("Juz");
}

/**
 * Unified "create reading session" dialog used by both the Dashboard
 * (Continue / Start New) and the Session tab's FAB. Previously the two
 * screens had divergent dialogs — the dashboard exposed PAGE/JUZ tabs,
 * while the Session tab only had a barebones "Target Pages" input —
 * which made the same conceptual action feel inconsistent. Centralising
 * here means a future tweak (e.g. an end-page input, a juz preview)
 * lands in one spot.
 *
 * @param initialPage the page the user is currently on; pre-fills the
 *                    "start from current" / juz hint.
 * @param sessionCount existing number of sessions; used to suggest the
 *                    next default name ("Session 3", "Session 4", …).
 * @param confirmLabel button label. Dashboard wants "Create & Start"
 *                    (jumps into the reader on confirm), Session tab
 *                    wants "Create" (just adds to the list). The
 *                    dialog itself stays neutral on what happens
 *                    next — the caller wires the post-confirm action.
 * @param onDismiss invoked when the user cancels or taps outside.
 * @param onConfirm receives the resolved (name, startPage, targetPages)
 *                  triple. The dialog closes itself before invoking
 *                  this so callers can navigate without a lingering
 *                  dialog overlay.
 */
@Composable
fun CreateSessionDialog(
    initialPage: Int,
    sessionCount: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, startPage: Int, targetPages: Int) -> Unit,
) {
    val context = LocalContext.current
    val defaultName = remember(sessionCount) {
        context.getString(R.string.session_new) + " ${sessionCount + 1}"
    }
    var nameInput by remember { mutableStateOf(defaultName) }
    var basis by remember { mutableStateOf(SessionBasis.PAGE) }
    var targetInput by remember { mutableStateOf("10") }
    var startFromCurrent by remember { mutableStateOf(true) }
    var customPageInput by remember { mutableStateOf(initialPage.toString()) }
    var juzInput by remember {
        mutableStateOf(QuranInfo.getJuzForPage(initialPage).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.dashboard_new_session_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(context.getString(R.string.dashboard_session_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Page / Juz toggle. Two FilterChips because Material 3
                // SegmentedButton landed after the Compose BOM this app
                // pins to (2024.05).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SessionBasis.entries.forEach { b ->
                        FilterChip(
                            selected = basis == b,
                            onClick = { basis = b },
                            label = { Text(b.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                when (basis) {
                    SessionBasis.PAGE -> {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { targetInput = it.filter { c -> c.isDigit() } },
                            label = { Text(context.getString(R.string.dashboard_target_pages)) },
                            // Inclusive range hint so users see the same
                            // wording the in-reader indicator uses.
                            supportingText = {
                                val start = if (startFromCurrent) initialPage
                                    else customPageInput.toIntOrNull()?.coerceIn(1, 604) ?: initialPage
                                val target = targetInput.toIntOrNull()?.coerceAtLeast(1) ?: 0
                                if (target > 0) {
                                    val end = (start + target - 1).coerceAtMost(604)
                                    Text(
                                        context.getString(
                                            R.string.dashboard_pages_range_summary,
                                            start,
                                            end,
                                            (end - start + 1),
                                        )
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
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
                                text = context.getString(
                                    R.string.dashboard_start_from_current,
                                    initialPage,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!startFromCurrent) {
                            OutlinedTextField(
                                value = customPageInput,
                                onValueChange = { customPageInput = it.filter { c -> c.isDigit() } },
                                label = { Text(context.getString(R.string.dashboard_start_page)) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    SessionBasis.JUZ -> {
                        OutlinedTextField(
                            value = juzInput,
                            onValueChange = { juzInput = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text(context.getString(R.string.dashboard_juz_label)) },
                            supportingText = {
                                val j = juzInput.toIntOrNull()?.coerceIn(1, 30)
                                if (j != null) {
                                    val (start, span) = QuranNavigationData.juzPageBounds(j)
                                    Text(
                                        context.getString(
                                            R.string.dashboard_pages_range_summary,
                                            start,
                                            start + span - 1,
                                            span,
                                        )
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val resolvedStart: Int
                val resolvedTarget: Int
                when (basis) {
                    SessionBasis.PAGE -> {
                        resolvedTarget = targetInput.toIntOrNull()?.coerceIn(1, 604) ?: 10
                        resolvedStart = if (startFromCurrent) initialPage
                            else customPageInput.toIntOrNull()?.coerceIn(1, 604) ?: initialPage
                    }
                    SessionBasis.JUZ -> {
                        val juz = juzInput.toIntOrNull()?.coerceIn(1, 30) ?: 1
                        val (start, span) = QuranNavigationData.juzPageBounds(juz)
                        resolvedStart = start
                        resolvedTarget = span
                    }
                }
                onConfirm(
                    nameInput.ifBlank { defaultName },
                    resolvedStart,
                    resolvedTarget,
                )
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.common_cancel))
            }
        }
    )
}
