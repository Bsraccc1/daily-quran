package com.quranreader.custom.data.local

import androidx.room.*
import com.quranreader.custom.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE page = :page LIMIT 1")
    suspend fun getBookmarkByPage(page: Int): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE page = :page")
    suspend fun deleteBookmarkByPage(page: Int)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()

    @Query("SELECT COUNT(*) FROM bookmarks WHERE page = :page")
    suspend fun isPageBookmarked(page: Int): Int
}
