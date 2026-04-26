package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ayah coordinate data for tap detection and highlighting
 * Stores the bounding box for each ayah on each page
 */
@Entity(tableName = "ayah_coordinates")
data class AyahCoordinate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val page: Int,
    val surah: Int,
    val ayah: Int,
    // Normalized coordinates (0.0 to 1.0)
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    /**
     * Check if a point (normalized coordinates) is within this ayah's bounds
     */
    fun contains(x: Float, y: Float): Boolean {
        return x >= minX && x <= maxX && y >= minY && y <= maxY
    }
}

/**
 * Highlighted ayah state
 */
data class HighlightedAyah(
    val page: Int,
    val surah: Int,
    val ayah: Int,
    val isBookmarked: Boolean = false
)
