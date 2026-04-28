package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.Recitation
import kotlinx.coroutines.flow.Flow

@Dao
interface RecitationDao {
    @Query("SELECT * FROM recitations ORDER BY reciterName")
    fun observeAll(): Flow<List<Recitation>>

    @Query("SELECT * FROM recitations ORDER BY reciterName")
    suspend fun listAll(): List<Recitation>

    @Query("SELECT * FROM recitations WHERE recitationId = :id LIMIT 1")
    suspend fun getById(id: Int): Recitation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Recitation>)

    @Query("UPDATE recitations SET audioUrlBase = :base WHERE recitationId = :id")
    suspend fun setAudioUrlBase(id: Int, base: String)

    @Query("SELECT COUNT(*) FROM recitations")
    suspend fun count(): Int
}
