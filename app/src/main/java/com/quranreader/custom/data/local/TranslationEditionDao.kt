package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.TranslationEdition
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationEditionDao {
    @Query("SELECT * FROM translation_editions ORDER BY languageName, name")
    fun observeAll(): Flow<List<TranslationEdition>>

    @Query("SELECT * FROM translation_editions ORDER BY languageName, name")
    suspend fun listAll(): List<TranslationEdition>

    @Query("SELECT * FROM translation_editions WHERE editionId = :editionId LIMIT 1")
    suspend fun getById(editionId: Int): TranslationEdition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(editions: List<TranslationEdition>)

    @Query("UPDATE translation_editions SET isDownloaded = :downloaded, verseCount = :count, lastDownloadedAt = :ts WHERE editionId = :editionId")
    suspend fun setDownloadStatus(editionId: Int, downloaded: Boolean, count: Int, ts: Long)

    @Query("DELETE FROM translation_editions WHERE editionId = :editionId")
    suspend fun delete(editionId: Int)

    @Query("SELECT * FROM translation_editions WHERE isDownloaded = 1 ORDER BY languageName, name")
    fun observeInstalled(): Flow<List<TranslationEdition>>
}
