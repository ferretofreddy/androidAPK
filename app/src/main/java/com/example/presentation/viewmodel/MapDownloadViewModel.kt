package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.DownloadedMapEntity
import com.example.data.local.GarminDashDatabase
import com.example.data.repository.MapDownloadManager
import com.example.data.worker.MapDownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class MapRegionSelection(
    val name: String = "Mi Region Offline",
    val minLat: Double = -12.05,
    val maxLat: Double = -12.00,
    val minLon: Double = -77.05,
    val maxLon: Double = -77.00,
    val zoomMin: Int = 10,
    val zoomMax: Int = 15
)

class MapDownloadViewModel(private val context: Context) : ViewModel() {

    private val downloadedMapDao = GarminDashDatabase.getDatabase(context).downloadedMapDao()
    private val downloadManager = MapDownloadManager(context)
    private val workManager = WorkManager.getInstance(context)

    val downloadedMaps: StateFlow<List<DownloadedMapEntity>> = downloadedMapDao.getAllDownloadedMaps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _regionSelection = MutableStateFlow(MapRegionSelection())
    val regionSelection: StateFlow<MapRegionSelection> = _regionSelection.asStateFlow()

    private val _estimatedTileCount = MutableStateFlow(0)
    val estimatedTileCount: StateFlow<Int> = _estimatedTileCount.asStateFlow()

    private val _estimatedSizeBytes = MutableStateFlow(0L)
    val estimatedSizeBytes: StateFlow<Long> = _estimatedSizeBytes.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        checkConnectivity()
        recalculateEstimates(_regionSelection.value)
        observeWorkManagerStatus()
    }

    private fun observeWorkManagerStatus() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("map_download").collect { workInfos ->
                if (workInfos.isNotEmpty()) {
                    val hasActive = workInfos.any { workInfo ->
                        workInfo.state == WorkInfo.State.ENQUEUED ||
                            workInfo.state == WorkInfo.State.RUNNING ||
                            workInfo.state == WorkInfo.State.BLOCKED
                    }
                    _isDownloading.value = hasActive
                } else {
                    _isDownloading.value = false
                }
            }
        }
    }

    fun checkConnectivity() {
        _isNetworkAvailable.value = downloadManager.isNetworkAvailable()
    }

    fun updateRegionSelection(selection: MapRegionSelection) {
        _regionSelection.value = selection
        recalculateEstimates(selection)
    }

    fun updateBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        val updated = _regionSelection.value.copy(
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon
        )
        _regionSelection.value = updated
        recalculateEstimates(updated)
    }

    fun updateZoomRange(zoomMin: Int, zoomMax: Int) {
        val updated = _regionSelection.value.copy(
            zoomMin = zoomMin,
            zoomMax = zoomMax
        )
        _regionSelection.value = updated
        recalculateEstimates(updated)
    }

    fun updateName(name: String) {
        _regionSelection.value = _regionSelection.value.copy(name = name)
    }

    private fun recalculateEstimates(selection: MapRegionSelection) {
        val tiles = downloadManager.calculateTileList(
            selection.minLat, selection.maxLat,
            selection.minLon, selection.maxLon,
            selection.zoomMin, selection.zoomMax
        )
        _estimatedTileCount.value = tiles.size
        _estimatedSizeBytes.value = downloadManager.estimateDownloadSize(tiles.size)
    }

    fun startDownload(mapIdToUpdate: Long = 0L) {
        if (_isDownloading.value) return

        checkConnectivity()
        if (!_isNetworkAvailable.value) {
            _errorMessage.value = "No hay conexión a internet. Verifique su red para descargar mapas."
            return
        }

        val sel = _regionSelection.value
        val inputData = Data.Builder()
            .putLong(MapDownloadWorker.KEY_MAP_ID, mapIdToUpdate)
            .putString(MapDownloadWorker.KEY_MAP_NAME, sel.name)
            .putDouble(MapDownloadWorker.KEY_MIN_LAT, sel.minLat)
            .putDouble(MapDownloadWorker.KEY_MAX_LAT, sel.maxLat)
            .putDouble(MapDownloadWorker.KEY_MIN_LON, sel.minLon)
            .putDouble(MapDownloadWorker.KEY_MAX_LON, sel.maxLon)
            .putInt(MapDownloadWorker.KEY_ZOOM_MIN, sel.zoomMin)
            .putInt(MapDownloadWorker.KEY_ZOOM_MAX, sel.zoomMax)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MapDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("map_download")
            .build()

        _isDownloading.value = true
        workManager.enqueue(workRequest)
        _errorMessage.value = null
    }

    fun updateMap(mapEntity: DownloadedMapEntity) {
        _regionSelection.value = MapRegionSelection(
            name = mapEntity.name,
            minLat = mapEntity.minLat,
            maxLat = mapEntity.maxLat,
            minLon = mapEntity.minLon,
            maxLon = mapEntity.maxLon,
            zoomMin = mapEntity.zoomMin,
            zoomMax = mapEntity.zoomMax
        )
        recalculateEstimates(_regionSelection.value)
        startDownload(mapIdToUpdate = mapEntity.id)
    }

    fun deleteMap(mapEntity: DownloadedMapEntity) {
        viewModelScope.launch {
            if (mapEntity.filePath.isNotEmpty()) {
                val file = File(mapEntity.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            downloadedMapDao.deleteMap(mapEntity)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MapDownloadViewModel(context) as T
        }
    }
}
