package com.example.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.floor

data class TileCoordinate(
    val z: Int,
    val x: Int,
    val y: Int
)

class MapDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "MapDownloadManager"
        private const val DEFAULT_TILE_URL_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        private val USER_AGENT = com.example.BuildConfig.APPLICATION_ID
        private const val AVERAGE_TILE_SIZE_BYTES = 20_000L // ~20KB per tile
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun calculateTileList(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        zoomMin: Int,
        zoomMax: Int
    ): List<TileCoordinate> {
        val tiles = mutableListOf<TileCoordinate>()
        val safeZoomMin = zoomMin.coerceIn(1, 19)
        val safeZoomMax = zoomMax.coerceIn(safeZoomMin, 19)

        for (z in safeZoomMin..safeZoomMax) {
            val minX = minOf(lonToTileX(minLon, z), lonToTileX(maxLon, z))
            val maxX = maxOf(lonToTileX(minLon, z), lonToTileX(maxLon, z))
            val minY = minOf(latToTileY(maxLat, z), latToTileY(minLat, z))
            val maxY = maxOf(latToTileY(maxLat, z), latToTileY(minLat, z))

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    tiles.add(TileCoordinate(z, x, y))
                }
            }
        }
        return tiles
    }

    fun estimateDownloadSize(tileCount: Int): Long {
        return tileCount * AVERAGE_TILE_SIZE_BYTES
    }

    suspend fun downloadAndBuildMbtiles(
        mapName: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        zoomMin: Int,
        zoomMax: Int,
        tileUrlTemplate: String = DEFAULT_TILE_URL_TEMPLATE,
        onProgress: suspend (downloaded: Int, total: Int, bytes: Long) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): File = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            throw IOException("Sin conexión a internet. No es posible iniciar la descarga de mapa.")
        }

        val mbTilesManager = MBTilesManager(context)
        val mbtilesDir = mbTilesManager.getMbtilesDirectory()
        val sanitizedFileName = mapName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_") + ".mbtiles"
        val outputFile = File(mbtilesDir, sanitizedFileName)

        val db = SQLiteDatabase.openOrCreateDatabase(outputFile, null)
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT);")
            db.execSQL("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row);")

            // Insert Metadata
            db.beginTransaction()
            try {
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('name', ?);", arrayOf(mapName))
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('type', 'baselayer');")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('version', '1.1');")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('description', 'GarminDash Offline Map Archive');")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('format', 'png');")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('bounds', ?);", arrayOf("$minLon,$minLat,$maxLon,$maxLat"))
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('minzoom', ?);", arrayOf(zoomMin.toString()))
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('maxzoom', ?);", arrayOf(zoomMax.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val activeTileTemplate = if (tileUrlTemplate == DEFAULT_TILE_URL_TEMPLATE) {
                TileSourceConfig(context).getActiveTileUrlTemplate()
            } else {
                tileUrlTemplate
            }

            val tileList = calculateTileList(minLat, maxLat, minLon, maxLon, zoomMin, zoomMax)
            val totalTiles = tileList.size
            var downloadedCount = 0
            var totalBytesDownloaded = 0L

            val semaphore = Semaphore(2) // Max 2 concurrent requests respecting OSM tile usage policy

            // Batch processing with sqlite transaction batches for performance
            val chunkSize = 20
            val chunks = tileList.chunked(chunkSize)

            for (chunk in chunks) {
                if (isCancelled()) {
                    Log.w(TAG, "Descarga cancelada por el usuario.")
                    break
                }

                val downloadedTilesInBatch = coroutineScope {
                    chunk.map { tile ->
                        async {
                            semaphore.withPermit {
                                if (isCancelled()) return@async null
                                val result = fetchTileDataWithRetry(tile, activeTileTemplate)
                                delay(150L) // Rate limiting throttle between requests
                                result
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                if (downloadedTilesInBatch.isNotEmpty()) {
                    db.beginTransaction()
                    try {
                        for ((tile, bytes) in downloadedTilesInBatch) {
                            val tmsY = (1 shl tile.z) - 1 - tile.y
                            db.execSQL(
                                "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?);",
                                arrayOf<Any>(tile.z, tile.x, tmsY, bytes)
                            )
                            downloadedCount++
                            totalBytesDownloaded += bytes.size
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }

                onProgress(downloadedCount, totalTiles, totalBytesDownloaded)
                delay(40) // Rate limiting throttle
            }

            Log.i(TAG, "Descarga de mapa completada: $downloadedCount / $totalTiles tiles ($totalBytesDownloaded bytes)")
            outputFile
        } finally {
            db.close()
        }
    }

    private suspend fun fetchTileDataWithRetry(
        tile: TileCoordinate,
        urlTemplate: String
    ): Pair<TileCoordinate, ByteArray>? {
        val url = urlTemplate
            .replace("{z}", tile.z.toString())
            .replace("{x}", tile.x.toString())
            .replace("{y}", tile.y.toString())

        var attempt = 0
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            return Pair(tile, bytes)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Attempt $attempt failed for tile z=${tile.z} x=${tile.x} y=${tile.y}: ${e.message}")
            }
            if (attempt < maxAttempts) {
                delay(300L * attempt) // Exponential backoff
            }
        }
        return null
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        var x = floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
        if (x < 0) x = 0
        if (x >= (1 shl zoom)) x = (1 shl zoom) - 1
        return x
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        var y = floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
        if (y < 0) y = 0
        if (y >= (1 shl zoom)) y = (1 shl zoom) - 1
        return y
    }
}
