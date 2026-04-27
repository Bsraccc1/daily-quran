package com.quranreader.custom.data.repository

import com.quranreader.custom.data.local.BookmarkDao
import com.quranreader.custom.data.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) {
    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    suspend fun getBookmarkByPage(page: Int): Bookmark? = bookmarkDao.getBookmarkByPage(page)

    suspend fun addBookmark(page: Int, surah: Int? = null, ayah: Int? = null): Long {
        val bookmark = Bookmark(
            page = page,
            surah = surah,
            ayah = ayah,
            timestamp = System.currentTimeMillis()
        )
        return bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun addBookmarkRaw(bookmark: Bookmark): Long = bookmarkDao.insertBookmark(bookmark)

    suspend fun removeBookmark(bookmark: Bookmark) = bookmarkDao.deleteBookmark(bookmark)

    suspend fun removeBookmarkByPage(page: Int) = bookmarkDao.deleteBookmarkByPage(page)

    suspend fun clearAll() = bookmarkDao.clearAllBookmarks()

    suspend fun isPageBookmarked(page: Int): Boolean = bookmarkDao.isPageBookmarked(page) > 0

    // ── Per-ayah API ─────────────────────────────────────────────────
    // The reader's slide-up action panel toggles bookmarks against the
    // *highlighted ayah*, not the page. These methods key off the full
    // (page, surah, ayah) triple so per-ayah bookmarks on the same
    // page coexist without stomping on each other.

    suspend fun findBookmarkByAyah(page: Int, surah: Int, ayah: Int): Bookmark? =
        bookmarkDao.findBookmarkByAyah(page, surah, ayah)

    /**
     * Reactive `true` while a bookmark exists for the (page, surah, ayah)
     * triple. The ViewModel uses [kotlinx.coroutines.flow.flatMapLatest]
     * to swap subscriptions whenever the user picks a different ayah,
     * so the bottom-panel bookmark icon updates instantly when another
     * client (e.g. the bookmarks list) inserts or removes the row.
     */
    fun observeAyahBookmarked(page: Int, surah: Int, ayah: Int): Flow<Boolean> =
        bookmarkDao.observeAyahBookmarkCount(page, surah, ayah).map { it > 0 }

    suspend fun removeBookmarkByAyah(page: Int, surah: Int, ayah: Int) =
        bookmarkDao.deleteBookmarkByAyah(page, surah, ayah)
}
