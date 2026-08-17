package com.example.data.repository

import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import java.io.File

/**
 * Manager for local MBTiles vector and raster map archives in osmdroid.
 * Uses MBTilesFileArchive.getDatabaseFileArchive(file) to attach archives
 * for 100% offline map rendering, combined with dynamic hybrid online/offline tile providers.
 */
class MBTilesManager(private val context: Context) {

    private val TAG = "GarminMBTilesManager"

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
        Configuration.getInstance().userAgentValue = "GarminDash/1.0 (Android)"

        // Configure osmdroid parameters and memory tile cache limits
        Configuration.getInstance().osmdroidBasePath = getMbtilesDirectory()
        Configuration.getInstance().osmdroidTileCache = File(getMbtilesDirectory(), "cache")
        Configuration.getInstance().cacheMapTileCount = 100 // Cap memory tile cache size
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 50 * 1024 * 1024L // 50MB max disk cache

        val hasInternet = MapDownloadManager(context).isNetworkAvailable()
        mapView.setUseDataConnection(hasInternet)

        val mbtilesFiles = listAvailableMbtilesFiles()
        val targetFile = selectedMbtilesFile ?: mbtilesFiles.firstOrNull()

        val registerReceiver = SimpleRegisterReceiver(context)
        val tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE

        val providers = mutableListOf<MapTileModuleProviderBase>()

        var archiveLoadedMsg: String? = null

        if (targetFile != null && targetFile.exists()) {
            try {
                // Register provider class for .mbtiles extension
                ArchiveFileFactory.registerArchiveFileProvider(
                    MBTilesFileArchive::class.java,
                    "mbtiles"
                )

                // Instantiate explicit MBTiles database file archive
                val mbtilesArchive: IArchiveFile = MBTilesFileArchive.getDatabaseFileArchive(targetFile)

                val fileArchiveProvider = MapTileFileArchiveProvider(
                    registerReceiver,
                    tileSource,
                    arrayOf(mbtilesArchive)
                )
                providers.add(fileArchiveProvider)
                archiveLoadedMsg = "Mapa MBTiles cargado: ${targetFile.name}"
                Log.i(TAG, "Attached MBTiles database file archive: ${targetFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MBTiles database archive: ${e.message}")
            }
        }

        if (hasInternet) {
            val template = TileSourceConfig(context).getActiveTileUrlTemplate()
            val baseUrl = template.substringBefore("{").trimEnd('/')
            val extension = template.substringAfter(".png", ".png")
            val onlineTileSource = XYTileSource(
                "online",
                0,
                19,
                256,
                extension,
                arrayOf(baseUrl)
            )
            val networkProvider = MapTileDownloader(onlineTileSource)
            providers.add(networkProvider)
        }

        if (providers.isNotEmpty()) {
            val tileProviderArray = MapTileProviderArray(
                tileSource,
                registerReceiver,
                providers.toTypedArray()
            )
            mapView.setTileProvider(tileProviderArray)

            return when {
                archiveLoadedMsg != null && hasInternet -> "$archiveLoadedMsg (Modo Híbrido Online/Offline)"
                archiveLoadedMsg != null -> archiveLoadedMsg
                hasInternet -> "Modo online activo (Fuente de tiles conectada)."
                else -> "Modo mapa offline activo. Coloca archivos .mbtiles en la carpeta 'GarminDash_MBTiles'."
            }
        }

        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        return "Modo mapa offline activo. Coloca archivos .mbtiles en la carpeta 'GarminDash_MBTiles'."
    }
}

