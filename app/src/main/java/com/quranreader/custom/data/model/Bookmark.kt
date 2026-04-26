package com.quranreader.custom.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val page: Int,
    val surah: Int? = null,
    val ayah: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
