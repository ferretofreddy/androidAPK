package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MapMarkerDao {

    @Query("SELECT * FROM map_markers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MapMarkerEntity>>

    @Query("SELECT * FROM map_markers")
    suspend fun getAll(): List<MapMarkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: MapMarkerEntity): Long

    @Query("UPDATE map_markers SET isVisible = :visible WHERE id = :id")
    suspend fun setVisible(id: Long, visible: Boolean)

    @Query("DELETE FROM map_markers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
