package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedMapDao {

    @Query("SELECT * FROM downloaded_maps ORDER BY downloadDateTimestamp DESC")
    fun getAllDownloadedMaps(): Flow<List<DownloadedMapEntity>>

    @Query("SELECT * FROM downloaded_maps WHERE id = :id")
    suspend fun getMapById(id: Long): DownloadedMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: DownloadedMapEntity): Long

    @Update
    suspend fun updateMap(map: DownloadedMapEntity)

    @Delete
    suspend fun deleteMap(map: DownloadedMapEntity)

    @Query("DELETE FROM downloaded_maps WHERE id = :id")
    suspend fun deleteMapById(id: Long)
}
