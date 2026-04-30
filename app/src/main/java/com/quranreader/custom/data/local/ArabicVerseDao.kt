package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.ArabicVerse

/**
 * Read API for the bundled `arabic_verses` table. Writes happen only
 * once during seeding (see
 * [com.quranreader.custom.data.seed.ArabicVerseSeeder]). All queries
 * are `suspend` — Translation reader reads on a background dispatcher.
 */
@Dao
interface ArabicVerseDao {
    @Query(
        "SELECT * FROM arabic_verses WHERE surahNumber = :surah " +
            "AND ayahNumber BETWEEN :from AND :to ORDER BY ayahNumber"
    )
    suspend fun getRange(surah: Int, from: Int, to: Int): List<ArabicVerse>

    @Query("SELECT * FROM arabic_verses WHERE surahNumber = :surah ORDER BY ayahNumber")
    suspend fun getSurah(surah: Int): List<ArabicVerse>

    @Query("SELECT * FROM arabic_verses WHERE surahNumber = :surah AND ayahNumber = :ayah LIMIT 1")
    suspend fun get(surah: Int, ayah: Int): ArabicVerse?

    @Query("SELECT COUNT(*) FROM arabic_verses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(verses: List<ArabicVerse>)
}
