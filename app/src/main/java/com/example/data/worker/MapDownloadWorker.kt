package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.DownloadedMapEntity
import com.example.data.local.GarminDashDatabase
import com.example.data.repository.MapDownloadManager
import java.io.File

class MapDownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "MapDownloadWorker"
        const val CHANNEL_ID = "map_download_channel"
        const val NOTIFICATION_ID = 2002

        const val KEY_MAP_ID = "key_map_id"
        const val KEY_MAP_NAME = "key_map_name"
        const val KEY_MIN_LAT = "key_min_lat"
        const val KEY_MAX_LAT = "key_max_lat"
        const val KEY_MIN_LON = "key_min_lon"
        const val KEY_MAX_LON = "key_max_lon"
        const val KEY_ZOOM_MIN = "key_zoom_min"
        const val KEY_ZOOM_MAX = "key_zoom_max"

        const val KEY_PROGRESS_DOWNLOADED = "key_progress_downloaded"
        const val KEY_PROGRESS_TOTAL = "key_progress_total"
        const val KEY_PROGRESS_BYTES = "key_progress_bytes"
        const val KEY_FILE_PATH = "key_file_path"
    }

    private val downloadManager = MapDownloadManager(appContext)
    private val database = GarminDashDatabase.getDatabase(appContext)
    private val downloadedMapDao = database.downloadedMapDao()

    override suspend fun doWork(): Result {
        val mapName = inputData.getString(KEY_MAP_NAME) ?: "Mapa_Garmin"
        val minLat = inputData.getDouble(KEY_MIN_LAT, 0.0)
        val maxLat = inputData.getDouble(KEY_MAX_LAT, 0.0)
        val minLon = inputData.getDouble(KEY_MIN_LON, 0.0)
        val maxLon = inputData.getDouble(KEY_MAX_LON, 0.0)
        val zoomMin = inputData.getInt(KEY_ZOOM_MIN, 10)
        val zoomMax = inputData.getInt(KEY_ZOOM_MAX, 15)
        var mapId = inputData.getLong(KEY_MAP_ID, 0L)

        createNotificationChannel()
        try {
            setForeground(createForegroundInfo("Iniciando descarga de mapa: $mapName", 0, 100))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo establecer el estado Foreground para WorkManager: ${e.message}")
        }

        if (!downloadManager.isNetworkAvailable()) {

            Log.e(TAG, "Sin red para iniciar la descarga de mapas.")
            return Result.failure(workDataOf("error" to "Sin conexión a internet"))
        }

        val tileList = downloadManager.calculateTileList(minLat, maxLat, minLon, maxLon, zoomMin, zoomMax)
        val totalTiles = tileList.size

        // Insert initial database record
        if (mapId == 0L) {
            val initialEntity = DownloadedMapEntity(
                name = mapName,
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
                downloadDateTimestamp = System.currentTimeMillis(),
                sizeBytes = 0L,
                filePath = "",
                totalTiles = totalTiles,
                downloadedTiles = 0,
                isCompleted = false
            )
            mapId = downloadedMapDao.insertMap(initialEntity)
        }

        return try {
            val outputFile = downloadManager.downloadAndBuildMbtiles(
                mapName = mapName,
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon,
                zoomMin = zoomMin,
                zoomMax = zoomMax,
                onProgress = { downloaded, total, bytes ->
                    val percentage = if (total > 0) (downloaded * 100) / total else 0
                    val contentText = "Descargando '$mapName': $downloaded/$total tiles (${bytes / (1024 * 1024)} MB)"

                    try {
                        setForeground(createForegroundInfo(contentText, percentage, 100))
                    } catch (e: Exception) {
                        // Ignore foreground notification update error if restricted
                    }
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS_DOWNLOADED to downloaded,
                            KEY_PROGRESS_TOTAL to total,
                            KEY_PROGRESS_BYTES to bytes
                        )
                    )

                    // Periodically update DB
                    if (downloaded % 10 == 0 || downloaded == total) {
                        val currentEntity = downloadedMapDao.getMapById(mapId)
                        if (currentEntity != null) {
                            downloadedMapDao.updateMap(
                                currentEntity.copy(
                                    downloadedTiles = downloaded,
                                    sizeBytes = bytes,
                                    downloadDateTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                isCancelled = { isStopped }
            )

            if (isStopped) {
                Log.w(TAG, "Descarga cancelada por WorkManager.")
                Result.failure(workDataOf("error" to "Descarga cancelada por el usuario"))
            } else {
                // Update final record as completed
                val completedEntity = downloadedMapDao.getMapById(mapId)
                if (completedEntity != null) {
                    downloadedMapDao.updateMap(
                        completedEntity.copy(
                            filePath = outputFile.absolutePath,
                            downloadedTiles = totalTiles,
                            sizeBytes = outputFile.length(),
                            downloadDateTimestamp = System.currentTimeMillis(),
                            isCompleted = true
                        )
                    )
                }

                updateNotification("Descarga de mapa completada", "'$mapName' listo para uso offline.", true)

                Result.success(
                    workDataOf(
                        KEY_MAP_ID to mapId,
                        KEY_FILE_PATH to outputFile.absolutePath
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la descarga de mapa: ${e.message}", e)
            updateNotification("Error descargando mapa", e.message ?: "Fallo de red", true)
            Result.failure(workDataOf("error" to (e.message ?: "Error desconocido")))
        }
    }

    private fun createForegroundInfo(contentText: String, progress: Int, maxProgress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("GarminDash - Descarga de Mapas")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(maxProgress, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, contentText: String, isFinished: Boolean) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(if (isFinished) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setOngoing(!isFinished)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas de Mapas Offline",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso de descarga de archivos .mbtiles"
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
