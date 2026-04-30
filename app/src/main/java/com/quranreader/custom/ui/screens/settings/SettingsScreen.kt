package com.quranreader.custom.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.R
import com.quranreader.custom.data.audio.Reciters
import com.quranreader.custom.data.preferences.AutoSaveMode
import com.quranreader.custom.data.preferences.ReadingMode
import com.quranreader.custom.ui.MainActivity
import com.quranreader.custom.ui.components.animated.ExpandableSection
import com.quranreader.custom.ui.viewmodel.AudioViewModel
import com.quranreader.custom.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Settings Screen — v3.0 redesign (REQ-011).
 *
 * Grouped cards w/ icons + expandable detail sections:
 *  - Display Theme (4 palettes + Material You)
 *  - Language
 *  - Reading
 *  - Audio Downloads (manage audio cache)
 *  - Memorization (hifz history)
 *  - Data (clear bookmarks)
 *  - About (incl. Mushaf Madinah attribution)
 *
 * Each card uses [ExpandableSection] for animated tap-to-expand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToManageDownloads: () -> Unit = {},
    onNavigateToMemorizationHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    audioViewModel: AudioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val themeId by viewModel.themeId.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val currentReciter by audioViewModel.currentReciter.collectAsState()
    var showClearBookmarksDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingLanguageCode by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Card: Display Theme ──────────────────────────────────────────────
        item {
            SettingsCard {
                ExpandableSection(
                    title = context.getString(R.string.settings_display_theme),
                    icon = Icons.Default.Palette,
                    initiallyExpanded = true
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeOptionRow(
                            name = context.getString(R.string.settings_theme_zamrud),
                            lightId = "zamrud_light",
                            darkId = "zamrud_dark",
                            primaryLightColor = Color(0xFF1B6B45),
                            bgLightColor = Color(0xFFF8F5F0),
                            primaryDarkColor = Color(0xFF78D9A5),
                            bgDarkColor = Color(0xFF131612),
                            currentThemeId = themeId,
                            onSelect = { viewModel.setThemeId(it) }
                        )
                        Divider()
                        ThemeOptionRow(
                            name = context.getString(R.string.settings_theme_teal),
                            lightId = "teal_light",
                            darkId = "teal_dark",
                            primaryLightColor = Color(0xFF1A5F8D),
                            bgLightColor = Color(0xFFF3F7FB),
                            primaryDarkColor = Color(0xFF7AC8E8),
                            bgDarkColor = Color(0xFF0D1318),
                            currentThemeId = themeId,
                            onSelect = { viewModel.setThemeId(it) }
                        )
                        Divider()
                        ThemeOptionRow(
                            name = context.getString(R.string.settings_theme_amber),
                            lightId = "amber_light",
                            darkId = "amber_dark",
                            primaryLightColor = Color(0xFF8B5E0A),
                            bgLightColor = Color(0xFFFBF8F2),
                            primaryDarkColor = Color(0xFFFAC775),
                            bgDarkColor = Color(0xFF17120A),
                            currentThemeId = themeId,
                            onSelect = { viewModel.setThemeId(it) }
                        )
                        Divider()
                        ThemeOptionRow(
                            name = context.getString(R.string.settings_theme_indigo),
                            lightId = "indigo_light",
                            darkId = "indigo_dark",
                            primaryLightColor = Color(0xFF303083),
                            bgLightColor = Color(0xFFF5F4FC),
                            primaryDarkColor = Color(0xFFB0ACFF),
                            bgDarkColor = Color(0xFF0E0D18),
                            currentThemeId = themeId,
                            onSelect = { viewModel.setThemeId(it) }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Divider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setThemeId("material_you") }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            context.getString(R.string.settings_theme_material_you),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            context.getString(R.string.settings_theme_material_you_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                RadioButton(
                                    selected = themeId == "material_you",
                                    onClick = { viewModel.setThemeId("material_you") }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // ── Card: Language ───────────────────────────────────────────────────
        item {
            SettingsCard {
                ExpandableSection(
                    title = context.getString(R.string.settings_language),
                    icon = Icons.Default.Language,
                    initiallyExpanded = false
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            context.getString(R.string.settings_app_language),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LanguageButton(
                                label = context.getString(R.string.settings_language_english),
                                flag = "🇬🇧",
                                isSelected = appLanguage == "en",
                                onClick = {
                                    if (appLanguage != "en") {
                                        pendingLanguageCode = "en"
                                        viewModel.setLanguage("en")
                                        showRestartDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            LanguageButton(
                                label = context.getString(R.string.settings_language_indonesian),
                                flag = "🇮🇩",
                                isSelected = appLanguage == "id",
                                onClick = {
                                    if (appLanguage != "id") {
                                        pendingLanguageCode = "id"
                                        viewModel.setLanguage("id")
                                        showRestartDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Card: Reading ────────────────────────────────────────────────────
        item {
            val continueReadingLimit by viewModel.continueReadingLimit.collectAsState()
            val autoSaveSeconds by viewModel.autoSavePageSeconds.collectAsState()
            val autoSaveMode by viewModel.autoSaveMode.collectAsState()
            val autoSavePageCount by viewModel.autoSavePageCount.collectAsState()
            var limitInput by remember { mutableStateOf(continueReadingLimit.toString()) }
            var autoSaveInput by remember { mutableStateOf(autoSaveSeconds.toString()) }
            var autoSavePagesInput by remember { mutableStateOf(autoSavePageCount.toString()) }
            LaunchedEffect(continueReadingLimit) {
                limitInput = continueReadingLimit.toString()
            }
            LaunchedEffect(autoSaveSeconds) {
                autoSaveInput = autoSaveSeconds.toString()
            }
            LaunchedEffect(autoSavePageCount) {
                autoSavePagesInput = autoSavePageCount.toString()
            }
            val readingMode by viewModel.readingMode.collectAsState()
            SettingsCard {
                ExpandableSection(
                    title = "Reading",
                    icon = Icons.Default.MenuBook,
                    initiallyExpanded = false
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

                        // ── Reading Style ────────────────────────────
                        // Picks between Mushaf (page-by-page WebP) and
                        // Translation (per-juz scrollable verse list).
                        // Lives at the top of the Reading section because
                        // it gates which reader the rest of the settings
                        // (auto-save, page limits, etc.) apply to.
                        Text(
                            stringResource(R.string.settings_reading_style_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_reading_style_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = readingMode == ReadingMode.MUSHAF,
                                onClick = { viewModel.setReadingMode(ReadingMode.MUSHAF) },
                                label = {
                                    Text(stringResource(R.string.settings_reading_mode_mushaf))
                                },
                                leadingIcon = if (readingMode == ReadingMode.MUSHAF) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                            )
                            FilterChip(
                                selected = readingMode == ReadingMode.TRANSLATION,
                                onClick = { viewModel.setReadingMode(ReadingMode.TRANSLATION) },
                                label = {
                                    Text(stringResource(R.string.settings_reading_mode_translation))
                                },
                                leadingIcon = if (readingMode == ReadingMode.TRANSLATION) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Divider()
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Pages per 'Continue Reading'",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Set how many pages to add when you click 'Continue Reading' after completing a session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = limitInput,
                            onValueChange = {
                                limitInput = it.filter { c -> c.isDigit() }
                                val value = limitInput.toIntOrNull()
                                if (value != null && value in 1..50) {
                                    viewModel.setContinueReadingLimit(value)
                                }
                            },
                            label = { Text("Pages (1-50)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text("Current: $continueReadingLimit pages")
                            }
                        )

                        Spacer(Modifier.height(16.dp))
                        Divider()
                        Spacer(Modifier.height(16.dp))

                        // ── Auto-save trigger ────────────────────────
                        // Two parallel modes — BY_TIME (legacy dwell
                        // timer) and BY_PAGES (commit every N flips).
                        // The picker swaps which numeric field is
                        // visible so the user only edits the value
                        // that's actually driving their reader.
                        Text(
                            stringResource(R.string.settings_autosave_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_autosave_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(12.dp))

                        // Mode picker — FilterChip pair instead of a
                        // SegmentedButton because FilterChip ships
                        // out of experimental and matches the rest
                        // of the settings card aesthetic (tiny
                        // capsules, not buttons).
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = autoSaveMode == AutoSaveMode.BY_TIME,
                                onClick = { viewModel.setAutoSaveMode(AutoSaveMode.BY_TIME) },
                                label = {
                                    Text(stringResource(R.string.settings_autosave_mode_by_time))
                                },
                                leadingIcon = if (autoSaveMode == AutoSaveMode.BY_TIME) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                            )
                            FilterChip(
                                selected = autoSaveMode == AutoSaveMode.BY_PAGES,
                                onClick = { viewModel.setAutoSaveMode(AutoSaveMode.BY_PAGES) },
                                label = {
                                    Text(stringResource(R.string.settings_autosave_mode_by_pages))
                                },
                                leadingIcon = if (autoSaveMode == AutoSaveMode.BY_PAGES) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        // Show only the field for the active mode —
                        // editing a hidden field would be confusing
                        // (user thinks they changed the trigger but
                        // the chip in the reader keeps using the
                        // other one).
                        when (autoSaveMode) {
                            AutoSaveMode.BY_TIME -> OutlinedTextField(
                                value = autoSaveInput,
                                onValueChange = {
                                    autoSaveInput = it.filter { c -> c.isDigit() }
                                    val value = autoSaveInput.toIntOrNull()
                                    if (value != null && value in 1..60) {
                                        viewModel.setAutoSavePageSeconds(value)
                                    }
                                },
                                label = {
                                    Text(stringResource(R.string.settings_autosave_seconds_label))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = {
                                    Text(
                                        stringResource(
                                            R.string.settings_autosave_seconds_current,
                                            autoSaveSeconds,
                                        )
                                    )
                                }
                            )
                            AutoSaveMode.BY_PAGES -> OutlinedTextField(
                                value = autoSavePagesInput,
                                onValueChange = {
                                    autoSavePagesInput = it.filter { c -> c.isDigit() }
                                    val value = autoSavePagesInput.toIntOrNull()
                                    if (value != null && value in 1..50) {
                                        viewModel.setAutoSavePageCount(value)
                                    }
                                },
                                label = {
                                    Text(stringResource(R.string.settings_autosave_pages_label))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = {
                                    Text(
                                        stringResource(
                                            R.string.settings_autosave_pages_current,
                                            autoSavePageCount,
                                        )
                                    )
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Card: Reciter (v9.0) ─────────────────────────────────────────────
        // Pulls the curated everyayah-backed list from
        // [Reciters.DEFAULT_RECITERS]. The expanded set (12 reciters,
        // up from 3) covers the classical murattal recitations the
        // app needs to feel competitive with quran.com / Tarteel —
        // adding more later means appending to the static list and
        // wiring the quran.com `recitation_id` in
        // [com.quranreader.custom.data.audio.timing.ReciterRecitationMap]
        // so the highlight sync still hits.
        item {
            SettingsCard {
                ExpandableSection(
                    title = "Reciter",
                    icon = Icons.Default.MusicNote,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Reciters.DEFAULT_RECITERS.forEach { reciter ->
                            val selected = reciter.id == currentReciter.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        audioViewModel.setReciter(reciter.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = reciter.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Audio streams from everyayah.com. Recitations without verified " +
                                "quran.com timing data will play normally but lose per-ayah " +
                                "highlight sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Card: Audio Downloads (v3.0) ─────────────────────────────────────
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.Default.MusicNote,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "Manage Audio Downloads",
                    subtitle = "View + delete downloaded surah audio",
                    onClick = onNavigateToManageDownloads
                )
            }
        }

        // ── Card: Memorization (v3.0) ────────────────────────────────────────
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.Default.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Memorization History",
                    subtitle = "Past hifz sessions + resume",
                    onClick = onNavigateToMemorizationHistory
                )
            }
        }

        // ── Card: Data ───────────────────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearBookmarksDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            context.getString(R.string.settings_clear_bookmarks),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            context.getString(R.string.settings_clear_bookmarks_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ── Card: Clear All History ──────────────────────────────────────────
        // Nukes everything we treat as "user history": bookmarks, all
        // multi-session entries, the legacy active session, the
        // last-page pointer, lifetime reading stats, and every
        // memorization session row. Configuration (theme, language,
        // reciter, reminder time) is intentionally preserved — those
        // are settings, not history.
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearHistoryDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            context.getString(R.string.settings_clear_history),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            context.getString(R.string.settings_clear_history_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ── Card: About ──────────────────────────────────────────────────────
        item {
            SettingsCard {
                ExpandableSection(
                    title = context.getString(R.string.settings_about),
                    icon = Icons.Default.Info,
                    initiallyExpanded = false
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            context.getString(R.string.app_name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            context.getString(R.string.settings_version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )

                        Spacer(Modifier.height(4.dp))
                        Divider()
                        Spacer(Modifier.height(4.dp))

                        // ── Mushaf Madinah attribution (CC BY-NC-ND 4.0) ─────
                        // Required by the licence of the bundled colored
                        // Madinah Mushaf page set.
                        Text(
                            "Mushaf Madinah",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "King Fahd Glorious Qur'an Printing Complex (Madinah, KSA) — Hafs reading, standard #39, 1441 AH / 2019 edition.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            "Licensed under CC BY-NC-ND 4.0 via archive.org/details/Green-standard-39-1441",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }

        // Bottom spacing
        item { Spacer(Modifier.height(24.dp)) }
    }
    } // close Scaffold content slot

    // ── Clear Bookmarks Confirmation Dialog ──────────────────────────────────
    if (showClearBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showClearBookmarksDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(context.getString(R.string.bookmarks_clear_all) + "?") },
            text = {
                Text(context.getString(R.string.bookmarks_clear_confirm))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllBookmarks()
                    showClearBookmarksDialog = false
                }) {
                    Text(context.getString(R.string.bookmarks_clear_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBookmarksDialog = false }) {
                    Text(context.getString(R.string.common_cancel))
                }
            }
        )
    }

    // ── Clear All History Confirmation Dialog ────────────────────────────────
    // Two-step confirmation matches the bookmarks delete flow so a
    // muscle-memory tap doesn't nuke a year of reading progress. The
    // snackbar afterwards lets the user see something happened
    // (otherwise the screen looks identical because Settings doesn't
    // surface stats here).
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(context.getString(R.string.settings_clear_history_confirm_title)) },
            text = {
                Text(context.getString(R.string.settings_clear_history_confirm_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllHistory()
                    showClearHistoryDialog = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.settings_clear_history_done),
                            duration = SnackbarDuration.Short,
                        )
                    }
                }) {
                    Text(
                        context.getString(R.string.settings_clear_history_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(context.getString(R.string.common_cancel))
                }
            }
        )
    }

    // ── Restart App Dialog ───────────────────────────────────────────────────
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            icon = {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(context.getString(R.string.settings_language_changed)) },
            text = {
                Text(context.getString(R.string.settings_language_restart_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    MainActivity.restart(context)
                }) {
                    Text(context.getString(R.string.settings_restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(context.getString(R.string.settings_restart_later))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers — reusable card + nav row components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) { content() }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

// ── Theme Option Row ─────────────────────────────────────────────────────────

@Composable
private fun ThemeOptionRow(
    name: String,
    lightId: String,
    darkId: String,
    primaryLightColor: Color,
    bgLightColor: Color,
    primaryDarkColor: Color,
    bgDarkColor: Color,
    currentThemeId: String,
    onSelect: (String) -> Unit
) {
    val isLightSelected = currentThemeId == lightId
    val isDarkSelected = currentThemeId == darkId
    val isSelected = isLightSelected || isDarkSelected

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeVariantChip(
                label = "Light",
                primaryColor = primaryLightColor,
                bgColor = bgLightColor,
                isSelected = isLightSelected,
                onClick = { onSelect(lightId) },
                modifier = Modifier.weight(1f)
            )
            ThemeVariantChip(
                label = "Dark",
                primaryColor = primaryDarkColor,
                bgColor = bgDarkColor,
                isSelected = isDarkSelected,
                onClick = { onSelect(darkId) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeVariantChip(
    label: String,
    primaryColor: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            )
            Text(
                if (label == "Light") context.getString(R.string.settings_theme_light)
                else context.getString(R.string.settings_theme_dark),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LanguageButton(
    label: String,
    flag: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.onSurface
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = if (isSelected) 2.dp else 1.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                flag,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}
