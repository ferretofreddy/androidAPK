package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startTimeTimestamp: Long,
    val endTimeTimestamp: Long,
    val totalDistanceMeters: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainMeters: Double,
    val pointCount: Int,
    val gpxExportedPath: String? = null
)
