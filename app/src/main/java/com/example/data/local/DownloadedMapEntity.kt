package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_maps")
data class DownloadedMapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val zoomMin: Int,
    val zoomMax: Int,
    val downloadDateTimestamp: Long,
    val sizeBytes: Long,
    val filePath: String,
    val totalTiles: Int,
    val downloadedTiles: Int,
    val isCompleted: Boolean = false
)
