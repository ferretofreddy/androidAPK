package com.example.data.sensor

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager as AndroidSensorManager
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class CompassAccuracy {
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromSensorStatus(status: Int): CompassAccuracy = when (status) {
            AndroidSensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HIGH
            AndroidSensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> MEDIUM
            AndroidSensorManager.SENSOR_STATUS_ACCURACY_LOW -> LOW
            AndroidSensorManager.SENSOR_STATUS_UNRELIABLE -> UNRELIABLE
            else -> UNRELIABLE
        }
    }
}

/**
 * CompassCalibrationManager - Magnetometer Calibration & Declination Fusion Engine
 *
 * DESIGN & ALGORITHMS:
 * 1. LOW-PASS FILTER (Alpha = 0.20):
 *    - Choice: Circular Unit Vector Low-Pass Filter (x = cos(theta), y = sin(theta)).
 *    - Alpha value of 0.20 was chosen based on responsive tracking vs jitter reduction.
 *      It dampens high-frequency magnetometer noise while tracking rapid 90-degree turns
 *      within ~300ms without noticeable lag.
 *    - Converting heading angles to (cos, sin) unit vectors prior to low-pass filtering avoids
 *      discontinuity/glitches at the 0°/360° boundary.
 *
 * 2. GEOMAGNETIC DECLINATION FUSION (True North):
 *    - Uses Android native GeomagneticField(lat, lon, alt, timestamp) to calculate local magnetic declination.
 *    - Recalculation Trigger: On initial GPS fix and whenever distance displacement exceeds 10,000m (10km).
 *    - True Heading = (Magnetic Heading + Declination + 360) % 360.
 *
 * 3. CALIBRATION NEEDED DETECTION:
 *    - Monitors sensor accuracy status on magnetic field sensor.
 *    - If accuracy remains LOW or UNRELIABLE for >= 5,000ms (5s), triggers isCalibrationNeeded = true.
 *    - Resets isCalibrationNeeded = false when accuracy recovers to MEDIUM or HIGH.
 */
class CompassCalibrationManager {

    companion object {
        const val FILTER_ALPHA = 0.20f // Low-pass filter coefficient (0.20 = balanced responsiveness & stability)
        const val RECALCULATE_DECLINATION_DIST_METERS = 10000.0 // 10 km
        const val LOW_ACCURACY_TIMEOUT_MS = 5000L
    }

    private val _compassAccuracy = MutableStateFlow(CompassAccuracy.HIGH)
    val compassAccuracy: StateFlow<CompassAccuracy> = _compassAccuracy.asStateFlow()

    private val _isCalibrationNeeded = MutableStateFlow(false)
    val isCalibrationNeeded: StateFlow<Boolean> = _isCalibrationNeeded.asStateFlow()

    private val _magneticHeadingDegrees = MutableStateFlow(0f)
    val magneticHeadingDegrees: StateFlow<Float> = _magneticHeadingDegrees.asStateFlow()

    private val _trueHeadingDegrees = MutableStateFlow(0f)
    val trueHeadingDegrees: StateFlow<Float> = _trueHeadingDegrees.asStateFlow()

    private val _magneticDeclinationDegrees = MutableStateFlow(0f)
    val magneticDeclinationDegrees: StateFlow<Float> = _magneticDeclinationDegrees.asStateFlow()

    // Circular Unit Vector Low-Pass Filter State
    private var filteredSin = 0.0
    private var filteredCos = 1.0
    private var isFilterInitialized = false

    // Calibration Tracking State
    private var lowAccuracyStartTimeMs: Long? = null

    // Declination Tracking State
    private var lastDeclinationLocation: Location? = null

    /**
     * Process raw azimuth angle in degrees (0..360) with circular unit-vector low-pass filter.
     */
    fun processRawAzimuth(rawAzimuthDegrees: Float): Float {
        val rad = Math.toRadians(rawAzimuthDegrees.toDouble())
        val newSin = sin(rad)
        val newCos = cos(rad)

        if (!isFilterInitialized) {
            filteredSin = newSin
            filteredCos = newCos
            isFilterInitialized = true
        } else {
            // Apply Low Pass Filter to unit vector components
            filteredSin = (1 - FILTER_ALPHA) * filteredSin + FILTER_ALPHA * newSin
            filteredCos = (1 - FILTER_ALPHA) * filteredCos + FILTER_ALPHA * newCos
        }

        var smoothedDeg = Math.toDegrees(atan2(filteredSin, filteredCos)).toFloat()
        if (smoothedDeg < 0) smoothedDeg += 360f

        _magneticHeadingDegrees.value = smoothedDeg

        // Apply magnetic declination correction
        var trueHeading = (smoothedDeg + _magneticDeclinationDegrees.value) % 360f
        if (trueHeading < 0) trueHeading += 360f
        _trueHeadingDegrees.value = trueHeading

        return trueHeading
    }

    /**
     * Update geomagnetic declination from current GNSS location fix.
     */
    fun updateLocationForDeclination(location: Location) {
        val lastLoc = lastDeclinationLocation
        val distance = if (lastLoc != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastLoc.latitude, lastLoc.longitude,
                location.latitude, location.longitude,
                results
            )
            results[0].toDouble()
        } else {
            Double.MAX_VALUE
        }

        if (distance >= RECALCULATE_DECLINATION_DIST_METERS) {
            try {
                val geoField = GeomagneticField(
                    location.latitude.toFloat(),
                    location.longitude.toFloat(),
                    location.altitude.toFloat(),
                    location.time
                )
                _magneticDeclinationDegrees.value = geoField.declination
                lastDeclinationLocation = location
            } catch (e: Exception) {
                // Fallback: keep current declination
            }
        }
    }

    /**
     * Update magnetic sensor accuracy and check for calibration warning conditions.
     */
    fun onSensorAccuracyChanged(sensorType: Int, accuracyStatus: Int) {
        if (sensorType == Sensor.TYPE_MAGNETIC_FIELD || sensorType == Sensor.TYPE_ROTATION_VECTOR) {
            val accuracy = CompassAccuracy.fromSensorStatus(accuracyStatus)
            _compassAccuracy.value = accuracy

            val currentTime = System.currentTimeMillis()
            if (accuracy == CompassAccuracy.LOW || accuracy == CompassAccuracy.UNRELIABLE) {
                if (lowAccuracyStartTimeMs == null) {
                    lowAccuracyStartTimeMs = currentTime
                } else if (currentTime - lowAccuracyStartTimeMs!! >= LOW_ACCURACY_TIMEOUT_MS) {
                    _isCalibrationNeeded.value = true
                }
            } else {
                lowAccuracyStartTimeMs = null
                _isCalibrationNeeded.value = false
            }
        }
    }

    fun dismissCalibrationWarning() {
        _isCalibrationNeeded.value = false
    }

    fun reset() {
        isFilterInitialized = false
        filteredSin = 0.0
        filteredCos = 1.0
        lowAccuracyStartTimeMs = null
        _isCalibrationNeeded.value = false
    }
}
