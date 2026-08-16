package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM routes ORDER BY startTimeTimestamp DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?

    @Query("SELECT * FROM track_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    suspend fun getTrackPointsForRoute(routeId: Long): List<TrackPointEntity>

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRoute(routeId: Long)

    @Query("DELETE FROM track_points WHERE routeId = :routeId")
    suspend fun deleteTrackPoints(routeId: Long)

    @Transaction
    suspend fun deleteRouteWithPoints(routeId: Long) {
        deleteTrackPoints(routeId)
        deleteRoute(routeId)
    }
}
