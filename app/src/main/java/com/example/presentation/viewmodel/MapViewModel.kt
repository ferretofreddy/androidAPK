package com.example.presentation.viewmodel

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.GarminDashDatabase
import com.example.data.local.MapMarkerEntity
import com.example.data.local.TrackPointEntity
import com.example.data.repository.MBTilesManager
import com.example.data.repository.RouteRepository
import com.example.data.sensor.SensorManager
import com.example.domain.model.RouteRecordingState
import com.example.domain.model.RouteWithPoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MapViewModel(
    private val context: Context,
    val sensorManager: SensorManager,
    val routeRepository: RouteRepository
) : ViewModel() {

    val mbTilesManager = MBTilesManager(context)
    private val mapMarkerDao = GarminDashDatabase.getDatabase(context).mapMarkerDao()

    val markers: StateFlow<List<MapMarkerEntity>> = mapMarkerDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Recording State Management
    private val _recordingState = MutableStateFlow<RouteRecordingState>(RouteRecordingState.Idle)
    val recordingState: StateFlow<RouteRecordingState> = _recordingState.asStateFlow()

    private val _recordingStartTime = MutableStateFlow(0L)
    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _recordedDistanceMeters = MutableStateFlow(0.0)
    val recordedDistanceMeters: StateFlow<Double> = _recordedDistanceMeters.asStateFlow()

    private val _elevationGainMeters = MutableStateFlow(0.0)
    val elevationGainMeters: StateFlow<Double> = _elevationGainMeters.asStateFlow()

    private val _activeTrackPoints = MutableStateFlow<List<TrackPointEntity>>(emptyList())
    val activeTrackPoints: StateFlow<List<TrackPointEntity>> = _activeTrackPoints.asStateFlow()

    private val _startLocation = MutableStateFlow<Location?>(null)
    val startLocation: StateFlow<Location?> = _startLocation.asStateFlow()

    private val _loadedHistoricalRoute = MutableStateFlow<RouteWithPoints?>(null)
    val loadedHistoricalRoute: StateFlow<RouteWithPoints?> = _loadedHistoricalRoute.asStateFlow()

    private val _selectedMbtilesFile = MutableStateFlow<File?>(null)
    val selectedMbtilesFile: StateFlow<File?> = _selectedMbtilesFile.asStateFlow()

    private var recordingJob: Job? = null
    private var lastLocation: Location? = null
    private var lastAltitude: Double? = null

    fun selectMbtilesFile(file: File?) {
        _selectedMbtilesFile.value = file
    }

    fun loadHistoricalRoute(routeId: Long) {
        viewModelScope.launch {
            val route = routeRepository.getRouteWithPoints(routeId)
            _loadedHistoricalRoute.value = route
        }
    }

    fun clearHistoricalRoute() {
        _loadedHistoricalRoute.value = null
    }

    fun startRecording() {
        if (_recordingState.value is RouteRecordingState.Recording) return
        _recordingState.value = RouteRecordingState.Recording
        _recordingStartTime.value = System.currentTimeMillis()
        _recordingDurationSeconds.value = 0L
        _recordedDistanceMeters.value = 0.0
        _elevationGainMeters.value = 0.0
        _activeTrackPoints.value = emptyList()

        val currentLoc = sensorManager.currentLocation.value
        _startLocation.value = currentLoc
        lastLocation = currentLoc
        lastAltitude = currentLoc?.altitude ?: sensorManager.barometricAltitude.value

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (_recordingState.value is RouteRecordingState.Recording) {
                val delayMs = (sensorManager.samplingIntervalSeconds.value * 1000L).coerceAtLeast(1000L)
                delay(delayMs)
                _recordingDurationSeconds.value = (System.currentTimeMillis() - _recordingStartTime.value) / 1000L

                val currentLocation = sensorManager.currentLocation.value
                if (currentLocation != null) {
                    if (_startLocation.value == null) {
                        _startLocation.value = currentLocation
                    }

                    val currentAlt = sensorManager.barometricAltitude.value
                    val currentPressure = sensorManager.pressureHpa.value
                    val currentSpeed = if (currentLocation.hasSpeed()) currentLocation.speed * 3.6f else 0f

                    lastLocation?.let { prev ->
                        val dist = prev.distanceTo(currentLocation)
                        // Ignore displacements under 2.5 meters to suppress stationary GNSS jitter
                        if (dist >= 2.5) {
                            _recordedDistanceMeters.value += dist
                            lastLocation = currentLocation
                        }
                    } ?: run {
                        lastLocation = currentLocation
                    }

                    lastAltitude?.let { prevAlt ->
                        val diff = currentAlt - prevAlt
                        // Accumulate elevation gain when positive gain exceeds 0.8m threshold on fused altitude
                        if (diff >= 0.8) {
                            _elevationGainMeters.value += diff
                            lastAltitude = currentAlt
                        }
                    } ?: run {
                        lastAltitude = currentAlt
                    }

                    val point = TrackPointEntity(
                        routeId = 0L,
                        timestamp = System.currentTimeMillis(),
                        latitude = currentLocation.latitude,
                        longitude = currentLocation.longitude,
                        altitudeMeters = currentAlt,
                        speedKmh = currentSpeed,
                        pressureHpa = currentPressure
                    )

                    _activeTrackPoints.value = _activeTrackPoints.value + point
                }
            }
        }
    }

    fun stopAndSaveRecording(routeName: String, onSaved: (Long) -> Unit) {
        if (_recordingState.value !is RouteRecordingState.Recording) return
        recordingJob?.cancel()
        recordingJob = null

        val endTime = System.currentTimeMillis()
        val points = _activeTrackPoints.value

        viewModelScope.launch {
            val routeId = routeRepository.saveRecordedRoute(
                routeName = routeName,
                startTime = _recordingStartTime.value,
                endTime = endTime,
                trackPoints = points,
                totalDistanceMeters = _recordedDistanceMeters.value,
                elevationGainMeters = _elevationGainMeters.value
            )
            _recordingState.value = RouteRecordingState.Saved(routeId)
            onSaved(routeId)
        }
    }

    fun discardRecording() {
        recordingJob?.cancel()
        recordingJob = null
        _recordingState.value = RouteRecordingState.Idle
        _activeTrackPoints.value = emptyList()
        _recordedDistanceMeters.value = 0.0
        _recordingDurationSeconds.value = 0L
        _startLocation.value = null
    }

    fun addMarker(name: String, latitude: Double, longitude: Double, colorArgb: Int) {
        viewModelScope.launch {
            val marker = MapMarkerEntity(
                name = name,
                latitude = latitude,
                longitude = longitude,
                colorArgb = colorArgb,
                isVisible = true,
                createdAt = System.currentTimeMillis()
            )
            mapMarkerDao.insert(marker)
        }
    }

    fun toggleMarkerVisibility(id: Long) {
        viewModelScope.launch {
            val currentMarker = markers.value.find { it.id == id }
            if (currentMarker != null) {
                mapMarkerDao.setVisible(id, !currentMarker.isVisible)
            }
        }
    }

    fun deleteMarker(id: Long) {
        viewModelScope.launch {
            mapMarkerDao.deleteById(id)
        }
    }

    class Factory(
        private val context: Context,
        private val sensorManager: SensorManager,
        private val routeRepository: RouteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MapViewModel(context, sensorManager, routeRepository) as T
        }
    }
}
