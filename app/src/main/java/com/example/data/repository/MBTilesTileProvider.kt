package com.example.data.repository

import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import java.io.File

class MBTilesTileProvider(private val context: Context) {

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
        // Configure osmdroid user agent and cache directories locally
        Configuration.getInstance().osmdroidBasePath = getMbtilesDirectory()
        Configuration.getInstance().osmdroidTileCache = File(getMbtilesDirectory(), "cache")

        // Force osmdroid into offline mode
        mapView.setUseDataConnection(false)

        val mbtilesFiles = listAvailableMbtilesFiles()
        val targetFile = selectedMbtilesFile ?: mbtilesFiles.firstOrNull()

        if (targetFile != null && targetFile.exists()) {
            try {
                val archiveFile: IArchiveFile? = ArchiveFileFactory.getArchiveFile(targetFile)
                if (archiveFile != null) {
                    val registerReceiver = SimpleRegisterReceiver(context)
                    val tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE

                    val moduleProvider = MapTileFileArchiveProvider(
                        registerReceiver,
                        tileSource,
                        arrayOf(archiveFile)
                    )

                    val tileProviderArray = MapTileProviderArray(
                        tileSource,
                        registerReceiver,
                        arrayOf<MapTileModuleProviderBase>(moduleProvider)
                    )

                    mapView.setTileProvider(tileProviderArray)
                    Log.i(TAG, "Successfully attached offline MBTiles: ${targetFile.name}")
                    return "Cargado archivo offline: ${targetFile.name}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading MBTiles: ${e.message}")
            }
        }

        // Fallback offline map configuration
        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        return "Modo mapa offline activo. Coloca tus archivos .mbtiles en: ${getMbtilesDirectory().absolutePath}"
    }
}
