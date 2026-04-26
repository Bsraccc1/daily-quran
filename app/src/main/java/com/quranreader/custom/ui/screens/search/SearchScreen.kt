package com.quranreader.custom.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.delay

/**
 * Search Screen — NO TopAppBar
 * Uses a back button within the content area for navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReading: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchType by viewModel.searchType.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Back button + Search Bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            // v3.0 redesign: animated focus scale on the search bar
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val focusScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isFocused) 1.02f else 1.0f,
                animationSpec = com.quranreader.custom.ui.theme.Motion.standard(),
                label = "SearchBar.focusScale"
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .weight(1f)
                    .scale(focusScale),
                placeholder = { Text("Search surah, juz, or page...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                interactionSource = interactionSource
            )
        }

        // Search Type Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = searchType == SearchType.ALL,
                onClick = { viewModel.setSearchType(SearchType.ALL) },
                label = { Text("All") },
                leadingIcon = {
                    Icon(Icons.Default.Apps, null, Modifier.size(18.dp))
                }
            )
            FilterChip(
                selected = searchType == SearchType.SURAH,
                onClick = { viewModel.setSearchType(SearchType.SURAH) },
                label = { Text("Surah") },
                leadingIcon = {
                    Icon(Icons.Default.MenuBook, null, Modifier.size(18.dp))
                }
            )
            FilterChip(
                selected = searchType == SearchType.JUZ,
                onClick = { viewModel.setSearchType(SearchType.JUZ) },
                label = { Text("Juz") },
                leadingIcon = {
                    Icon(Icons.Default.Bookmark, null, Modifier.size(18.dp))
                }
            )
            FilterChip(
                selected = searchType == SearchType.PAGE,
                onClick = { viewModel.setSearchType(SearchType.PAGE) },
                label = { Text("Page") },
                leadingIcon = {
                    Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Search Results
        if (searchQuery.isEmpty()) {
            SearchSuggestions(
                onSuggestionClick = { suggestion ->
                    viewModel.updateSearchQuery(suggestion)
                }
            )
        } else if (searchResults.isEmpty()) {
            AnimatedNoResultsState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(searchResults, key = { _, r -> r.title }) { index, result ->
                    StaggeredResultCard(
                        index = index,
                        result = result,
                        onClick = {
                            when (result) {
                                is SearchResult.SurahResult -> {
                                    onNavigateToReading(result.startPage)
                                }
                                is SearchResult.JuzResult -> {
                                    onNavigateToReading(result.startPage)
                                }
                                is SearchResult.PageResult -> {
                                    onNavigateToReading(result.pageNumber)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaggeredResultCard(
    index: Int,
    result: SearchResult,
    onClick: () -> Unit
) {
    var visible by remember(result.title) { mutableStateOf(false) }
    LaunchedEffect(result.title) {
        delay((index * 50L).coerceAtMost(400L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = Motion.standard()) +
                slideInVertically(animationSpec = Motion.standard()) { it / 4 }
    ) {
        SearchResultCard(result = result, onClick = onClick)
    }
}

@Composable
private fun AnimatedNoResultsState() {
    val transition = rememberInfiniteTransition(label = "noResults.pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "noResults.scale"
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulse),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                "No results found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Try searching for a surah name, juz number, or page number.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SearchSuggestions(
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Popular Searches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val suggestions = listOf(
            "Al-Fatihah",
            "Al-Baqarah",
            "Al-Kahf",
            "Ya-Sin",
            "Ar-Rahman",
            "Juz 30",
            "Juz 1",
            "Page 1"
        )

        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                icon = {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                }
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (result) {
                    is SearchResult.SurahResult -> Icons.Default.MenuBook
                    is SearchResult.JuzResult -> Icons.Default.Bookmark
                    is SearchResult.PageResult -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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

// Search Result Types
sealed class SearchResult {
    abstract val title: String
    abstract val subtitle: String

    data class SurahResult(
        val surahNumber: Int,
        val surahName: String,
        val transliteration: String,
        val ayahCount: Int,
        val startPage: Int
    ) : SearchResult() {
        override val title = "$surahNumber. $surahName"
        override val subtitle = "$transliteration • $ayahCount ayat • Page $startPage"
    }

    data class JuzResult(
        val juzNumber: Int,
        val startPage: Int,
        val endPage: Int
    ) : SearchResult() {
        override val title = "Juz $juzNumber"
        override val subtitle = "Pages $startPage - $endPage"
    }

    data class PageResult(
        val pageNumber: Int,
        val surahName: String
    ) : SearchResult() {
        override val title = "Page $pageNumber"
        override val subtitle = surahName
    }
}

enum class SearchType {
    ALL, SURAH, JUZ, PAGE
}
