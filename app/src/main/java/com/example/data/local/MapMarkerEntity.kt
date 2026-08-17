package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "map_markers")
data class MapMarkerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val colorArgb: Int,
    val isVisible: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
