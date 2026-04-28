package com.quranreader.custom.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import com.quranreader.custom.data.model.SurahInfo
import com.quranreader.custom.ui.viewmodel.AyahInputError
import com.quranreader.custom.ui.viewmodel.SearchEvent
import com.quranreader.custom.ui.viewmodel.SearchViewModel

/**
 * Compact popup dialog implementing the only search surface in the
 * app: a single-page form that pairs a Surah picker with an inline
 * verse-number text field side-by-side, then jumps the reader to
 * the matching mushaf page with the verse pre-highlighted via the
 * bundled `ayahinfo.db` (the byproduct of the
 * `quran/ayah-detection` Python pipeline).
 *
 *  - Tap the **Surah** field → nested [SurahPickerDialog] popup.
 *  - The **Verse** field is a `Number`-keyboard text field — the
 *    user types the ayah and the surah's max-ayah count clamps it
 *    via live validation in [SearchViewModel].
 *
 * Sizing is DPI-agnostic: width = `min(92% of window, 480 dp)` clamped
 * to ≥ 280 dp; height = `min(85% of window, 640 dp)`. Together with
 * `usePlatformDefaultWidth = false` this looks correct from a 5"
 * 360 dp phone up through a 12" tablet without conditionals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyahSearchDialog(
    onDismiss: () -> Unit,
    onResult: (page: Int, surah: Int, ayah: Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.NavigateToAyah -> {
                    onDismiss()
                    onResult(event.page, event.surah, event.ayah)
                }
            }
        }
    }

    val handleClose = {
        viewModel.resetForReopen()
        onDismiss()
    }

    Dialog(
        onDismissRequest = handleClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val dialogMaxWidth = com.quranreader.custom.ui.components.responsivePanelMaxWidth(maxWidth)
            val dialogMaxHeight = (maxHeight * 0.85f).coerceAtMost(640.dp)

            Surface(
                modifier = Modifier
                    .widthIn(
                        min = com.quranreader.custom.ui.components.MIN_PANEL_WIDTH.coerceAtMost(maxWidth),
                        max = dialogMaxWidth,
                    )
                    .heightIn(max = dialogMaxHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DialogHeader(onClose = handleClose)
                    Spacer(Modifier.size(16.dp))
                    AyahLookupForm(viewModel = viewModel, onCancel = handleClose)
                }
            }
        }
    }
}

/**
 * Title row. Uses a small leading icon + title + subtitle stacked
 * vertically rather than a big circular badge — same information,
 * different visual rhythm.
 */
@Composable
private fun DialogHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.search_ayah_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.search_ayah_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.search_ayah_close),
            )
        }
    }
}

/**
 * Form body. Surah picker (clickable read-only field) and verse
 * number field laid out **side-by-side** in a single row — the
 * surah field flexes wider (1.4f) because it carries the localised
 * surah name, the verse field stays narrow (1f) because it only
 * ever holds 1–3 digits.
 *
 * Helper / error text sits in the supportingText slot of the verse
 * field so it tracks input changes without re-flowing the row.
 */
@Composable
private fun AyahLookupForm(
    viewModel: SearchViewModel,
    onCancel: () -> Unit,
) {
    val selectedSurah by viewModel.selectedSurah.collectAsStateWithLifecycle()
    val ayahInput by viewModel.ayahInput.collectAsStateWithLifecycle()
    val ayahError by viewModel.ayahError.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolvingAyah.collectAsStateWithLifecycle()
    val selectedVerse = ayahInput.toIntOrNull()

    var showSurahPicker by remember { mutableStateOf(false) }
    val errorMessage = ayahErrorMessage(ayahError)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // ── Surah selector — wider, clickable, opens picker ────
            // weight 2f vs 1f leaves 67% for the surah label and 33%
            // for the verse number, which only ever holds 1–3 digits.
            // The wider column is needed for long names like
            // "Al-Mumtahanah" or "Ash-Shu'ara" so they don't truncate
            // back to just the number prefix.
            FormSelector(
                modifier = Modifier.weight(2f),
                label = stringResource(R.string.search_ayah_field_surah),
                value = selectedSurah?.let { "${it.number}. ${it.englishName}" },
                placeholder = stringResource(R.string.search_ayah_field_surah_placeholder),
                onClick = { showSurahPicker = true },
            )

            // ── Verse field — narrow numeric keyboard input ────────
            val ayatLabel = if (selectedSurah != null) {
                stringResource(R.string.search_ayah_field_ayah_range, selectedSurah!!.ayahCount)
            } else {
                stringResource(R.string.search_ayah_field_ayah)
            }
            OutlinedTextField(
                value = ayahInput,
                onValueChange = viewModel::updateAyahInput,
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        text = ayatLabel,
                        // Long surah counts (e.g. "Ayat (1–286)") would
                        // otherwise wrap and push the field down, so
                        // we squeeze the label visually with a smaller
                        // style — Material lets the floated label shrink.
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                placeholder = { Text("1") },
                singleLine = true,
                enabled = selectedSurah != null,
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.submitAyahLookup() },
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    textAlign = TextAlign.Center,
                ),
            )
        }

        // Helper / error / disabled-state text immediately under the row.
        // Pulled out of supportingText so it spans the full dialog width
        // instead of just under the narrow verse field.
        val helperText = when {
            errorMessage != null -> errorMessage
            selectedSurah == null -> stringResource(R.string.search_ayah_field_ayah_disabled)
            else -> stringResource(R.string.search_ayah_input_max, selectedSurah!!.ayahCount)
        }
        Text(
            text = helperText,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (errorMessage != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Action row ─────────────────────────────────────────────
        val canSubmit = !isResolving &&
            ayahError == null &&
            selectedSurah != null &&
            selectedVerse?.let { it in 1..selectedSurah!!.ayahCount } == true
        ActionRow(
            isResolving = isResolving,
            canSubmit = canSubmit,
            onCancel = onCancel,
            onSubmit = { viewModel.submitAyahLookup() },
        )
    }

    if (showSurahPicker) {
        SurahPickerDialog(
            viewModel = viewModel,
            onPick = { surah ->
                viewModel.selectSurah(surah)
                showSurahPicker = false
            },
            onDismiss = { showSurahPicker = false },
        )
    }
}

/**
 * Read-only field that opens the surah picker on tap. Visually we
 * render an outlined Surface with the label floating above the value
 * (similar to a Material 3 `OutlinedTextField`), but we don't reuse
 * `OutlinedTextField` itself because it always raises the soft
 * keyboard on focus — we want a popup picker instead.
 */
@Composable
private fun FormSelector(
    modifier: Modifier = Modifier,
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.DropdownList },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    // Without `softWrap = false` Compose will hard-wrap
                    // multi-word names like "Al-Mumtahanah" onto a
                    // second line, and `maxLines = 1` then drops that
                    // line — leaving the user staring at just the
                    // number prefix. Ellipsis at the end keeps the
                    // content visibly continuous.
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    isResolving: Boolean,
    canSubmit: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.search_ayah_cancel))
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(),
        ) {
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(stringResource(R.string.search_ayah_submit))
        }
    }
}

// ── Nested popup dialog ──────────────────────────────────────────────────────

/**
 * Searchable surah picker. Opened on top of [AyahSearchDialog] when
 * the Surah field is tapped. Reuses the parent ViewModel's filtered
 * surah list (driven by `ayahPickerQuery`) so the search state
 * survives reopens within a single dialog session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahPickerDialog(
    viewModel: SearchViewModel,
    onPick: (SurahInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerQuery by viewModel.ayahPickerQuery.collectAsStateWithLifecycle()
    val filteredSurahs by viewModel.filteredSurahs.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val w = (maxWidth * 0.92f).coerceAtMost(480.dp)
            val h = (maxHeight * 0.85f).coerceAtMost(640.dp)

            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = w)
                    .heightIn(max = h)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.search_ayah_pick_surah_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_ayah_close),
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = viewModel::updateAyahPickerQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(stringResource(R.string.search_ayah_picker_placeholder))
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (pickerQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateAyahPickerQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.search_ayah_clear),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    if (filteredSurahs.isEmpty()) {
                        EmptyPickerState()
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(filteredSurahs, key = { it.number }) { surah ->
                                SurahPickerRow(surah = surah, onClick = { onPick(surah) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurahPickerRow(
    surah: SurahInfo,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = surah.number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.englishName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.search_ayah_picker_subtitle,
                        surah.ayahCount,
                        surah.startPage,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                text = surah.arabicName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyPickerState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.search_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Maps a [AyahInputError] into the user-facing copy. */
@Composable
private fun ayahErrorMessage(error: AyahInputError?): String? = when (error) {
    null -> null
    AyahInputError.NoSurahSelected -> stringResource(R.string.search_ayah_error_no_surah)
    AyahInputError.NotANumber -> stringResource(R.string.search_ayah_error_not_a_number)
    AyahInputError.BelowMin -> stringResource(R.string.search_ayah_error_below_min)
    is AyahInputError.AboveMax ->
        stringResource(R.string.search_ayah_error_above_max, error.max)
    AyahInputError.NotFound -> stringResource(R.string.search_ayah_error_not_found)
}
