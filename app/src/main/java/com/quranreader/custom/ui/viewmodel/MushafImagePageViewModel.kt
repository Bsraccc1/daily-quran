package com.quranreader.custom.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.local.ayahinfo.AyahInfoRepository
import com.quranreader.custom.data.local.ayahinfo.GlyphEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Loads the per-glyph coordinates for one mushaf page image. One
 * instance per visible page (keyed by page number in the renderer).
 *
 * Coordinates live in the bundled `ayahinfo.db` and are in the page
 * image's native pixel space (1024 × 1656). The renderer rescales
 * them on the fly to the actual displayed size.
 */
@HiltViewModel
class MushafImagePageViewModel @Inject constructor(
    private val repository: AyahInfoRepository,
) : ViewModel() {

    var glyphs: List<GlyphEntity> by mutableStateOf(emptyList())
        private set

    var loadedPage: Int = -1
        private set

    fun loadPage(pageNumber: Int) {
        if (pageNumber == loadedPage && glyphs.isNotEmpty()) return
        loadedPage = pageNumber
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.glyphsForPage(pageNumber)
            }
            // Guard against rapid page swipes overwriting newer data.
            if (loadedPage == pageNumber) {
                glyphs = result
            }
        }
    }
}
