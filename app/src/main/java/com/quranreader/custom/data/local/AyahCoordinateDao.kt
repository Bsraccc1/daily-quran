package com.quranreader.custom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranreader.custom.data.model.AyahCoordinate
import kotlinx.coroutines.flow.Flow

@Dao
interface AyahCoordinateDao {
    
    @Query("SELECT * FROM ayah_coordinates WHERE page = :page")
    suspend fun getCoordinatesForPage(page: Int): List<AyahCoordinate>
    
    @Query("SELECT * FROM ayah_coordinates WHERE page = :page")
    fun getCoordinatesForPageFlow(page: Int): Flow<List<AyahCoordinate>>
    
    @Query("SELECT * FROM ayah_coordinates WHERE page = :page AND surah = :surah AND ayah = :ayah LIMIT 1")
    suspend fun getCoordinate(page: Int, surah: Int, ayah: Int): AyahCoordinate?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordinates(coordinates: List<AyahCoordinate>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordinate(coordinate: AyahCoordinate)
    
    @Query("DELETE FROM ayah_coordinates WHERE page = :page")
    suspend fun deleteCoordinatesForPage(page: Int)
    
    @Query("DELETE FROM ayah_coordinates")
    suspend fun clearAll()
}
