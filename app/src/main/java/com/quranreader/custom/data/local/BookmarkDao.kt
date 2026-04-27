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

    // ── Per-ayah lookups ─────────────────────────────────────────────
    // All three columns must match — `surah`/`ayah` are nullable in the
    // schema, so a "page-only" bookmark (both null) is distinct from a
    // per-ayah bookmark on the same page. These queries explicitly key
    // off the (page, surah, ayah) triple so the two coexist cleanly.

    /** Fetch the per-ayah bookmark row, if any. */
    @Query("""
        SELECT * FROM bookmarks
        WHERE page = :page AND surah = :surah AND ayah = :ayah
        LIMIT 1
    """)
    suspend fun findBookmarkByAyah(page: Int, surah: Int, ayah: Int): Bookmark?

    /** Reactive count → 0 means "not bookmarked", non-zero means "bookmarked". */
    @Query("""
        SELECT COUNT(*) FROM bookmarks
        WHERE page = :page AND surah = :surah AND ayah = :ayah
    """)
    fun observeAyahBookmarkCount(page: Int, surah: Int, ayah: Int): Flow<Int>

    /** Delete the per-ayah bookmark for the (page, surah, ayah) triple. */
    @Query("""
        DELETE FROM bookmarks
        WHERE page = :page AND surah = :surah AND ayah = :ayah
    """)
    suspend fun deleteBookmarkByAyah(page: Int, surah: Int, ayah: Int)
}
