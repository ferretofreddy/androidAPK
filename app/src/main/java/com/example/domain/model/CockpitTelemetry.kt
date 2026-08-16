package com.example.domain.model

/**
 * Real-time telemetry state for GarminDash cockpit displays.
 */
data class CockpitTelemetry(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val headingDegrees: Float = 0f,       // Compass azimuth 0..360
    val pitchDegrees: Float = 0f,         // Nose up/down tilt
    val rollDegrees: Float = 0f,          // Left/right tilt
    val atmosphericPressureHpa: Float = 1013.25f, // hPa / mbar
    val speedKmh: Float = 0f,             // Speed in km/h
    val gpsAccuracyMeters: Float = 0f,
    val compassAccuracy: com.example.data.sensor.CompassAccuracy = com.example.data.sensor.CompassAccuracy.HIGH,
    val isCalibrationNeeded: Boolean = false,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val isGpsSearching: Boolean = true,
    val elevationGainMeters: Double = 0.0,
    val isRecordingRoute: Boolean = false,
    val recordedDistanceKm: Double = 0.0,
    val recordedDurationSeconds: Long = 0L,
    val sampledPointCount: Int = 0
)
