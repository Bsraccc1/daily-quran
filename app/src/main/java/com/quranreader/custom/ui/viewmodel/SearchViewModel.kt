package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.ui.screens.search.SearchResult
import com.quranreader.custom.ui.screens.search.SearchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchType = MutableStateFlow(SearchType.ALL)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()
    
    init {
        // Observe search query and perform search
        viewModelScope.launch {
            searchQuery
                .debounce(300) // Wait 300ms after user stops typing
                .combine(searchType) { query, type -> Pair(query, type) }
                .collect { (query, type) ->
                    performSearch(query, type)
                }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }
    
    fun setSearchType(type: SearchType) {
        _searchType.value = type
    }
    
    private fun performSearch(query: String, type: SearchType) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        val results = mutableListOf<SearchResult>()
        
        // Search Surahs
        if (type == SearchType.ALL || type == SearchType.SURAH) {
            results.addAll(searchSurahs(query))
        }
        
        // Search Juz
        if (type == SearchType.ALL || type == SearchType.JUZ) {
            results.addAll(searchJuz(query))
        }
        
        // Search Pages
        if (type == SearchType.ALL || type == SearchType.PAGE) {
            results.addAll(searchPages(query))
        }
        
        _searchResults.value = results
    }
    
    private fun searchSurahs(query: String): List<SearchResult.SurahResult> {
        val lowerQuery = query.lowercase()
        val results = mutableListOf<SearchResult.SurahResult>()
        
        // Simple transliteration map for popular surahs
        val transliterations = mapOf(
            1 to "Al-Fatihah",
            2 to "Al-Baqarah",
            3 to "Ali 'Imran",
            18 to "Al-Kahf",
            36 to "Ya-Sin",
            55 to "Ar-Rahman",
            67 to "Al-Mulk",
            112 to "Al-Ikhlas",
            113 to "Al-Falaq",
            114 to "An-Nas"
        )
        
        for (i in 1..114) {
            val name = QuranInfo.getSurahName(i)
            val transliteration = transliterations[i] ?: "Surah $i"
            val ayahCount = QuranInfo.getAyahCount(i)
            val startPage = QuranInfo.getStartPage(i)
            
            // Match by number, name, or transliteration
            if (i.toString() == query ||
                name.lowercase().contains(lowerQuery) ||
                transliteration.lowercase().contains(lowerQuery)
            ) {
                results.add(
                    SearchResult.SurahResult(
                        surahNumber = i,
                        surahName = name,
                        transliteration = transliteration,
                        ayahCount = ayahCount,
                        startPage = startPage
                    )
                )
            }
        }
        
        return results
    }
    
    private fun searchJuz(query: String): List<SearchResult.JuzResult> {
        val results = mutableListOf<SearchResult.JuzResult>()
        val lowerQuery = query.lowercase()
        
        // Juz data (page ranges)
        val juzData = mapOf(
            1 to (1 to 21),
            2 to (22 to 41),
            3 to (42 to 61),
            4 to (62 to 81),
            5 to (82 to 101),
            6 to (102 to 121),
            7 to (122 to 141),
            8 to (142 to 161),
            9 to (162 to 181),
            10 to (182 to 201),
            11 to (202 to 221),
            12 to (222 to 241),
            13 to (242 to 261),
            14 to (262 to 281),
            15 to (282 to 301),
            16 to (302 to 321),
            17 to (322 to 341),
            18 to (342 to 361),
            19 to (362 to 381),
            20 to (382 to 401),
            21 to (402 to 421),
            22 to (422 to 441),
            23 to (442 to 461),
            24 to (462 to 481),
            25 to (482 to 501),
            26 to (502 to 521),
            27 to (522 to 541),
            28 to (542 to 561),
            29 to (562 to 581),
            30 to (582 to 604)
        )
        
        juzData.forEach { (juzNumber, pages) ->
            if (lowerQuery.contains("juz") && lowerQuery.contains(juzNumber.toString()) ||
                lowerQuery == juzNumber.toString()
            ) {
                results.add(
                    SearchResult.JuzResult(
                        juzNumber = juzNumber,
                        startPage = pages.first,
                        endPage = pages.second
                    )
                )
            }
        }
        
        return results
    }
    
    private fun searchPages(query: String): List<SearchResult.PageResult> {
        val results = mutableListOf<SearchResult.PageResult>()
        
        // Try to parse as page number
        val pageNumber = query.toIntOrNull()
        if (pageNumber != null && pageNumber in 1..604) {
            // Find which surah this page belongs to
            val surahNumber = findSurahForPage(pageNumber)
            val surahName = QuranInfo.getSurahName(surahNumber)
            
            results.add(
                SearchResult.PageResult(
                    pageNumber = pageNumber,
                    surahName = surahName
                )
            )
        }
        
        return results
    }
    
    private fun findSurahForPage(page: Int): Int {
        for (i in 1..114) {
            val startPage = QuranInfo.getStartPage(i)
            val endPage = if (i < 114) {
                QuranInfo.getStartPage(i + 1) - 1
            } else {
                604
            }
            
            if (page in startPage..endPage) {
                return i
            }
        }
        return 1
    }
}
