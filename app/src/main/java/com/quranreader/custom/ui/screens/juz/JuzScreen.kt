package com.quranreader.custom.ui.screens.juz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranreader.custom.R
import com.quranreader.custom.data.QuranNavigationData
import com.quranreader.custom.data.audio.Reciters
import com.quranreader.custom.data.model.HizbInfo
import com.quranreader.custom.data.model.JuzInfo
import com.quranreader.custom.data.model.QuranNavigationTab
import com.quranreader.custom.data.model.SurahInfo
import com.quranreader.custom.ui.components.SurahDownloadButton
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.viewmodel.ReadingViewModel
import com.quranreader.custom.data.QuranInfo
import androidx.compose.animation.core.animateFloatAsState

/**
 * Juz Screen — v3.0 redesign (REQ-014).
 *
 *  - Material 3 [TabRow] with animated underline indicator (replaces chip row).
 *  - Surah cards show a progress ring of pages-read-vs-total.
 *  - Tap a surah card to expand: download button + bookmark count + Open button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuzScreen(
    onNavigateToReading: (Int) -> Unit,
    readingViewModel: ReadingViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(QuranNavigationTab.JUZ) }
    val context = LocalContext.current
    val lastPage by readingViewModel.currentPage.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tabs with animated indicator ─────────────────────────────────────
        val tabs = QuranNavigationTab.values().toList()
        val selectedIndex = tabs.indexOf(selectedTab)
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, tab ->
                val label = when (tab) {
                    QuranNavigationTab.JUZ -> context.getString(R.string.juz_tab)
                    QuranNavigationTab.SURAH -> context.getString(R.string.surah_tab)
                    QuranNavigationTab.HIZB -> context.getString(R.string.hizb_tab)
                }
                Tab(
                    selected = index == selectedIndex,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            QuranNavigationTab.JUZ -> JuzList(onNavigateToReading)
            QuranNavigationTab.SURAH -> SurahList(onNavigateToReading, lastPage)
            QuranNavigationTab.HIZB -> HizbList(onNavigateToReading)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// JUZ LIST
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JuzList(onNavigateToReading: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(QuranNavigationData.juzList) { juz ->
            JuzCard(juz = juz, onClick = { onNavigateToReading(juz.startPage) })
        }
    }
}

@Composable
private fun JuzCard(juz: JuzInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        juz.number.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    context.getString(R.string.juz_number, juz.number),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    context.getString(R.string.juz_pages, juz.startPage, juz.endPage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "${juz.endPage - juz.startPage + 1} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SURAH LIST — w/ progress rings + expandable detail
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SurahList(
    onNavigateToReading: (Int) -> Unit,
    lastPage: Int
) {
    var expandedSurah by remember { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(QuranNavigationData.surahList, key = { it.number }) { surah ->
            ExpandableSurahCard(
                surah = surah,
                lastPage = lastPage,
                expanded = expandedSurah == surah.number,
                onToggleExpand = {
                    expandedSurah = if (expandedSurah == surah.number) null else surah.number
                },
                onOpen = { onNavigateToReading(surah.startPage) }
            )
        }
    }
}

@Composable
private fun ExpandableSurahCard(
    surah: SurahInfo,
    lastPage: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current

    // Compute end page from next surah's start (or 604 for last surah).
    val endPage = if (surah.number == 114) 604 else QuranInfo.getStartPage(surah.number + 1) - 1
    val totalPages = (endPage - surah.startPage + 1).coerceAtLeast(1)
    val readPages = (lastPage - surah.startPage + 1).coerceIn(0, totalPages)
    val progress = readPages.toFloat() / totalPages.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = Motion.standard(),
        label = "SurahCard.progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress ring around surah number
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 4.dp
                    )
                    Text(
                        surah.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        surah.arabicName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        surah.englishName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            context.getString(R.string.surah_ayahs, surah.ayahCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (surah.isMakki)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                if (surah.isMakki)
                                    context.getString(R.string.surah_makki)
                                else
                                    context.getString(R.string.surah_madani),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (surah.isMakki)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text("•", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Expanded detail row
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = Motion.standard()) + fadeIn(animationSpec = Motion.standard()),
                exit = shrinkVertically(animationSpec = Motion.standard()) + fadeOut(animationSpec = Motion.short())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pages ${surah.startPage} – $endPage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Read $readPages of $totalPages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SurahDownloadButton(
                            reciterId = Reciters.DEFAULT_RECITERS[0].id,
                            surahNumber = surah.number
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open in mushaf")
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HIZB LIST
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HizbList(onNavigateToReading: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(QuranNavigationData.hizbList) { hizb ->
            HizbCard(hizb = hizb, onClick = { onNavigateToReading(hizb.startPage) })
        }
    }
}

@Composable
private fun HizbCard(hizb: HizbInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        hizb.number.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "¼ ${hizb.quarter}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    context.getString(R.string.hizb_number, hizb.number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    context.getString(R.string.hizb_quarter, hizb.quarter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        context.getString(R.string.juz_number, hizb.juzNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text(
                        context.getString(R.string.home_page) + " ${hizb.startPage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
