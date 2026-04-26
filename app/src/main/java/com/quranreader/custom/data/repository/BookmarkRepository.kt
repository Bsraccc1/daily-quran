package com.quranreader.custom.data.repository

import com.quranreader.custom.data.local.BookmarkDao
import com.quranreader.custom.data.model.Bookmark
import kotlinx.coroutines.flow.Flow
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
}
