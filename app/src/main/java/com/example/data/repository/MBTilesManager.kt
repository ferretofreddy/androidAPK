package com.example.data.repository

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manager for local MBTiles vector and raster map archives in osmdroid.
 * Uses in-memory session tile caching with MapTileDownloader (no disk cache)
 * and optional local MBTiles archive provider.
 */
class MBTilesManager(private val context: Context) {

    private val TAG = "GarminMBTiles"

    fun getMbtilesDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "GarminDash_MBTiles")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun listAvailableMbtilesFiles(): List<File> {
        val dir = getMbtilesDirectory()
        val files = dir.listFiles { _, name ->
            name.lowercase().endsWith(".mbtiles") || name.lowercase().endsWith(".sqlite")
        }
        return files?.toList() ?: emptyList()
    }

    fun setupOfflineMapView(mapView: MapView, selectedMbtilesFile: File?): String {
        // OsmDroid global configuration: in-memory session tile cache (1500 tiles)
        Configuration.getInstance().userAgentValue = "GarminDash/1.0 (Android)"
        Configuration.getInstance().cacheMapTileCount = 1500.toShort()

        val hasInternet = MapDownloadManager(context).isNetworkAvailable()

        // 1. Resolve custom/preset online TileSource with exact base URL and extension
        val config = TileSourceConfig(context)
        val template = config.getActiveTileUrlTemplate()
        val preset = config.getActivePreset()

        val baseUrl = if (template.contains("{z}")) {
            val beforeZ = template.substringBefore("{z}")
            if (beforeZ.endsWith("/")) beforeZ else "$beforeZ/"
        } else if (template.contains("{x}")) {
            val beforeX = template.substringBefore("{x}")
            if (beforeX.endsWith("/")) beforeX else "$beforeX/"
        } else {
            val before = template.substringBefore("{")
            if (before.endsWith("/")) before else "$before/"
        }

        val extension = if (template.contains("{y}")) {
            template.substringAfter("{y}")
        } else if (template.contains(".png")) {
            ".png" + template.substringAfter(".png")
        } else {
            ".png"
        }

        val activeTileSource = XYTileSource(
            preset.id,
            0,
            19,
            256,
            extension,
            arrayOf(baseUrl)
        )

        // Diagnostic Logging
        Log.i(TAG, "--- osmDroid Setup Diagnostics ---")
        Log.i(TAG, "hasInternet: $hasInternet")
        Log.i(TAG, "TileSource: name=${activeTileSource.name()}, baseUrl=$baseUrl, extension=$extension")

        // Direct OkHttp Tile Diagnostic Download Test (Zoom 8 Center)
        val testLat = 9.9347
        val testLon = -84.0875
        val testZoom = 8
        val testTileX = ((testLon + 180.0) / 360.0 * (1 shl testZoom)).toInt().coerceIn(0, (1 shl testZoom) - 1)
        val testLatRad = Math.toRadians(testLat.coerceIn(-85.05112878, 85.05112878))
        val testTileY = ((1.0 - Math.log(Math.tan(testLatRad) + 1.0 / Math.cos(testLatRad)) / Math.PI) / 2.0 * (1 shl testZoom)).toInt().coerceIn(0, (1 shl testZoom) - 1)
        val testTileUrl = "$baseUrl$testZoom/$testTileX/$testTileY$extension"

        if (hasInternet) {
            Thread {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(testTileUrl)
                        .header("User-Agent", "GarminDash/1.0 (Android)")
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodySize = response.body?.bytes()?.size ?: 0
                    Log.i(TAG, "OkHttp direct test tile fetch [$code]: $testTileUrl (Bytes: $bodySize)")
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "OkHttp direct test tile fetch failed: $testTileUrl - Error: ${e.message}")
                }
            }.start()
        }

        // Set data connection BEFORE configuring tile provider
        mapView.setUseDataConnection(hasInternet)

        val mbtilesFiles = listAvailableMbtilesFiles()
        val targetFile = selectedMbtilesFile ?: mbtilesFiles.firstOrNull()
        val registerReceiver = SimpleRegisterReceiver(context)

        var archiveLoadedMsg: String? = null
        val providers = mutableListOf<MapTileModuleProviderBase>()

        // a) Proveedor de archivo .mbtiles si existe archivo local
        if (targetFile != null && targetFile.exists()) {
            try {
                ArchiveFileFactory.registerArchiveFileProvider(
                    MBTilesFileArchive::class.java,
                    "mbtiles"
                )
                val mbtilesArchive: IArchiveFile = MBTilesFileArchive.getDatabaseFileArchive(targetFile)
                val fileArchiveProvider = MapTileFileArchiveProvider(
                    registerReceiver,
                    activeTileSource,
                    arrayOf(mbtilesArchive)
                )
                providers.add(fileArchiveProvider)
                archiveLoadedMsg = "MBTiles: ${targetFile.name}"
                Log.i(TAG, "Provider registered: ${fileArchiveProvider::class.java.simpleName} (archive=${targetFile.name})")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MBTiles database archive: ${e.message}")
            }
        }

        val approximater = MapTileApproximater()
        providers.add(approximater)

        // b) MapTileDownloader(activeTileSource) SIN TileWriter si hay internet
        if (hasInternet) {
            val downloader = MapTileDownloader(activeTileSource)
            approximater.addProvider(downloader)
            providers.add(downloader)
            Log.i(TAG, "Provider registered: ${downloader::class.java.simpleName} (tileSource=${activeTileSource.name()}, memory-only cache)")
        }

        val tileProviderArray = MapTileProviderArray(
            activeTileSource,
            registerReceiver,
            providers.toTypedArray()
        )

        mapView.setTileProvider(tileProviderArray)
        mapView.setTileSource(activeTileSource)

        return when {
            archiveLoadedMsg != null && hasInternet -> "$archiveLoadedMsg (Híbrido Online/Offline)"
            archiveLoadedMsg != null -> "$archiveLoadedMsg (Offline)"
            hasInternet -> "Modo online activo (${preset.name})"
            else -> "Sin conexión a internet. Sin archivo MBTiles cargado."
        }
    }
}

