package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.model.Bookmark
import com.quranreader.custom.data.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    val bookmarks = bookmarkRepository.getAllBookmarks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(bookmark)
        }
    }

    fun addBookmarkRaw(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.addBookmarkRaw(bookmark)
        }
    }
}
