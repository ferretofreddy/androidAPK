package com.example.presentation.viewmodel

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RouteRepository
import com.example.data.sensor.SensorManager
import com.example.domain.model.AltitudeSource
import com.example.domain.model.CockpitTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CockpitViewModel(
    private val context: Context,
    val sensorManager: SensorManager,
    val routeRepository: RouteRepository
) : ViewModel() {

    private val _samplingIntervalSeconds = MutableStateFlow(2)
    val samplingIntervalSeconds: StateFlow<Int> = _samplingIntervalSeconds.asStateFlow()

    val altitudeSource: StateFlow<AltitudeSource> = sensorManager.altitudeSource

    private data class SensorGroup(
        val location: Location?,
        val heading: Float,
        val pitch: Float,
        val roll: Float,
        val pressure: Float,
        val compassAccuracy: com.example.data.sensor.CompassAccuracy,
        val isCalibrationNeeded: Boolean
    )

    private val sensorGroupFlow = combine(
        sensorManager.currentLocation,
        sensorManager.headingDegrees,
        sensorManager.pitchDegrees,
        sensorManager.rollDegrees,
        sensorManager.pressureHpa
    ) { loc, heading, pitch, roll, pressure ->
        SensorGroup(
            loc,
            heading,
            pitch,
            roll,
            pressure,
            sensorManager.compassCalibrationManager.compassAccuracy.value,
            sensorManager.compassCalibrationManager.isCalibrationNeeded.value
        )
    }

    // Combined Telemetry StateFlow
    val telemetryState: StateFlow<CockpitTelemetry> = combine(
        sensorGroupFlow,
        sensorManager.altitudeSource,
        sensorManager.gnssStatusManager.gnssStatusData
    ) { s, altSrc, gnssData ->
        val loc = s.location
        val lat = loc?.latitude ?: 0.0
        val lon = loc?.longitude ?: 0.0
        val speed = if (loc != null && loc.hasSpeed()) loc.speed * 3.6f else 0f
        val accuracy = loc?.accuracy ?: 0f

        CockpitTelemetry(
            latitude = lat,
            longitude = lon,
            altitudeMeters = altSrc.altitudeMeters,
            headingDegrees = s.heading,
            pitchDegrees = s.pitch,
            rollDegrees = s.roll,
            atmosphericPressureHpa = s.pressure,
            speedKmh = speed,
            gpsAccuracyMeters = accuracy,
            compassAccuracy = s.compassAccuracy,
            isCalibrationNeeded = s.isCalibrationNeeded,
            satellitesUsed = gnssData.satellitesUsed,
            satellitesVisible = gnssData.satellitesVisible,
            isGpsSearching = loc == null || gnssData.isSearching
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CockpitTelemetry()
    )

    init {
        sensorManager.startSensorsAndLocation()
    }

    fun setSamplingInterval(seconds: Int) {
        val safeSeconds = seconds.coerceIn(1, 10)
        _samplingIntervalSeconds.value = safeSeconds
        sensorManager.updateSamplingInterval(safeSeconds)
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stopSensorsAndLocation()
    }

    class Factory(
        private val context: Context,
        private val sensorManager: SensorManager,
        private val routeRepository: RouteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CockpitViewModel(context, sensorManager, routeRepository) as T
        }
    }
}

