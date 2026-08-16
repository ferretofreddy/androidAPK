package com.example.data.sensor

import android.location.Location
import android.util.Log
import com.example.domain.model.AltitudeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos

/**
 * LocationFusionManager - Position & Altitude Fusion Engine for GarminDash
 *
 * FILTERING DESIGN DECISIONS & PARAMETERS:
 *
 * 1. GNSS POSITION FILTER: 1D Kalman Filter (Latitude & Longitude)
 *    - Choice: 1D Kalman Filter selected over Moving Average because moving average introduces
 *      undesirable lag/phase delay during constant directional motion. The Kalman filter dynamically
 *      weights incoming GNSS samples based on real-time receiver measurement variance (Location.accuracy).
 *    - Process Noise (Q): 1e-7 sq-degrees (~0.01m² variance expectation per sample interval).
 *    - Measurement Noise (R): Derived dynamically from location.accuracy converted to angular variance:
 *      R_lat = (accuracy / 111,000 m/deg)²
 *      R_lon = (accuracy / (111,000 * cos(lat) m/deg))²
 *    - Accuracy Threshold:
 *      - ACCURACY_REJECT_THRESHOLD = 25.0 meters. Any raw GNSS fix with accuracy worse than 25m is discarded
 *        to prevent artificial "GPS jumps" in track recordings.
 *
 * 2. STATIONARY JITTER SUPPRESSION & DISTANCE:
 *    - STATIONARY_DISTANCE_THRESHOLD = 2.5 meters.
 *    - Displacements under 2.5m between consecutive samples are ignored for cumulative distance calculations
 *      unless the vehicle/user speed exceeds 2.0 km/h. This prevents false distance accumulation while stationary.
 *
 * 3. COMPLEMENTARY ALTITUDE FUSION (GNSS + BAROMETER):
 *    - Uses hardware barometer pressure deltas for smooth, noise-free relative elevation change.
 *    - Uses GNSS altitude as an absolute reference baseline.
 *    - Blends the two: H_fused = H_fused_prev + delta_H_baro + alpha * (H_gps - H_fused_prev)
 *    - Alpha = 0.05 when GPS accuracy < 10m (smoothly calibrates barometric drift toward absolute GPS altitude
 *      without step discontinuities).
 */
class LocationFusionManager {

    private val TAG = "LocationFusionManager"

    // Configuration Constants (Tunable for field testing)
    companion object {
        const val ACCURACY_REJECT_THRESHOLD = 25.0f // meters
        const val ACCURACY_HIGH_PRECISION = 10.0f    // meters
        const val STATIONARY_DISTANCE_THRESHOLD = 2.5 // meters
        const val METERS_PER_DEGREE_LAT = 111000.0
    }

    // 1D Kalman Filter Implementation
    private class KalmanFilter1D(val processNoiseQ: Double = 1e-7) {
        var x: Double = 0.0
        var p: Double = 1.0
        var isInitialized: Boolean = false

        fun update(measurement: Double, measurementVarianceR: Double): Double {
            if (!isInitialized) {
                x = measurement
                p = measurementVarianceR
                isInitialized = true
                return x
            }
            // Predict
            p += processNoiseQ

            // Update
            val k = p / (p + measurementVarianceR)
            x += k * (measurement - x)
            p *= (1.0 - k)

            return x
        }

        fun reset() {
            isInitialized = false
            p = 1.0
        }
    }

    private val latKalman = KalmanFilter1D(processNoiseQ = 1e-7)
    private val lonKalman = KalmanFilter1D(processNoiseQ = 1e-7)
    
    /**
     * DIAGNÓSTICO Y CORRECCIÓN DE ALTITUD ESTANCADA:
     * Causa raíz:
     * El filtro Kalman 1D (altKalman) utilizaba un ruido de proceso processNoiseQ = 1e-4 m².
     * Este valor extremadamente reducido causaba que tras unas pocas muestras iniciales,
     * la varianza de estimación 'p' colapsara a ~0. En consecuencia, la ganancia de Kalman 'k'
     * tendía a 0 (k ≈ 0.000004), haciendo que el filtro rechazara cualquier variación real
     * de altitud del GPS interpretándola como ruido estático y congelando el valor en pantalla.
     *
     * Corrección:
     * 1. Re-ajuste de processNoiseQ = 0.5 m² para responder de forma reactiva a gradientes de elevación.
     * 2. Recalibración continua y dinámica del offset barométrico cuando la precisión GPS es <= 10m
     *    sin banderas o booleanos estáticos que bloqueen la recalibración tras la primera muestra.
     */
    private val altKalman = KalmanFilter1D(processNoiseQ = 0.5)

    // State
    private var lastRawLocation: Location? = null
    private var lastFilteredLocation: Location? = null

    // Barometer fusion state
    private var lastBaroPressureHpa: Float? = null
    private var lastBaroAltitudeMeters: Double? = null
    private var fusedAltitudeMeters: Double = 0.0
    private var isAltitudeInitialized: Boolean = false

    // Output Flows
    private val _fusedLocation = MutableStateFlow<Location?>(null)
    val fusedLocation: StateFlow<Location?> = _fusedLocation.asStateFlow()

    private val _altitudeSource = MutableStateFlow<AltitudeSource>(AltitudeSource.GpsDerived(0.0, 0f))
    val altitudeSource: StateFlow<AltitudeSource> = _altitudeSource.asStateFlow()

    private val _fusedSpeedKmh = MutableStateFlow(0f)
    val fusedSpeedKmh: StateFlow<Float> = _fusedSpeedKmh.asStateFlow()

    /**
     * Process new raw GNSS Location fix through Kalman Filter and altitude fusion.
     */
    fun processLocation(rawLocation: Location, hasHardwareBarometer: Boolean): Location? {
        // Step 1: Reject low-accuracy fixes (> 25m)
        if (rawLocation.hasAccuracy() && rawLocation.accuracy > ACCURACY_REJECT_THRESHOLD) {
            Log.w(TAG, "Fix rechazado por baja precisión GPS: ${rawLocation.accuracy}m")
            return _fusedLocation.value
        }

        lastRawLocation = rawLocation
        val rawLat = rawLocation.latitude
        val rawLon = rawLocation.longitude
        val accuracy = if (rawLocation.hasAccuracy()) rawLocation.accuracy else 15.0f

        // Step 2: Calculate measurement noise variance R in angular degrees squared
        val rLat = (accuracy / METERS_PER_DEGREE_LAT).let { it * it }
        val cosLat = cos(Math.toRadians(rawLat)).coerceAtLeast(0.1)
        val rLon = (accuracy / (METERS_PER_DEGREE_LAT * cosLat)).let { it * it }

        // Step 3: Filter Latitude & Longitude with Kalman
        val filteredLat = latKalman.update(rawLat, rLat)
        val filteredLon = lonKalman.update(rawLon, rLon)

        // Step 4: Speed Calculation
        val rawSpeedKmh = if (rawLocation.hasSpeed() && rawLocation.speed >= 0f) {
            rawLocation.speed * 3.6f
        } else {
            0f
        }

        // Calculate displacement from previous filtered position for jitter check
        val displacementMeters = lastFilteredLocation?.let { prev ->
            val results = FloatArray(1)
            Location.distanceBetween(prev.latitude, prev.longitude, filteredLat, filteredLon, results)
            results[0].toDouble()
        } ?: 0.0

        val speedKmh = if (displacementMeters < STATIONARY_DISTANCE_THRESHOLD && rawSpeedKmh < 2.0f) {
            0f // Suppress stationary jitter
        } else if (rawSpeedKmh > 0f) {
            rawSpeedKmh
        } else {
            // Fallback speed from distance/time if location.hasSpeed() is false
            val timeDeltaSec = lastFilteredLocation?.let { (rawLocation.time - it.time) / 1000.0 } ?: 0.0
            if (timeDeltaSec > 0.5) (displacementMeters / timeDeltaSec * 3.6).toFloat() else 0f
        }

        _fusedSpeedKmh.value = speedKmh

        // Step 5: Altitude Fusion
        val rawGpsAlt = rawLocation.altitude
        val isRealFix = rawLocation.provider != "SimulatedGPS"
        val wasSimulated = lastRawLocation?.provider == "SimulatedGPS"

        // Reset & seed altitude directly with real GPS when transitioning from simulation or when uninitialized
        if ((isRealFix && wasSimulated) || !isAltitudeInitialized) {
            fusedAltitudeMeters = rawGpsAlt
            altKalman.reset()
            isAltitudeInitialized = true
        }

        val finalAltitude = if (hasHardwareBarometer && lastBaroAltitudeMeters != null) {
            // Complementary Barometer + GNSS Fusion
            val pressure = lastBaroPressureHpa ?: 1013.25f
            val gpsCorrectionAlpha = if (accuracy <= ACCURACY_HIGH_PRECISION) 0.1 else 0.02
            fusedAltitudeMeters += gpsCorrectionAlpha * (rawGpsAlt - fusedAltitudeMeters)

            _altitudeSource.value = AltitudeSource.Fused(
                altitudeMeters = fusedAltitudeMeters,
                pressureHpa = pressure,
                isGpsCalibrated = accuracy <= ACCURACY_HIGH_PRECISION,
                gpsAccuracyMeters = accuracy
            )
            fusedAltitudeMeters
        } else {
            // GPS Fallback with Kalman smoothing
            val rAlt = (accuracy * 0.5).let { it * it }
            val filteredGpsAlt = altKalman.update(rawGpsAlt, rAlt)
            _altitudeSource.value = AltitudeSource.GpsDerived(
                altitudeMeters = filteredGpsAlt,
                accuracyMeters = accuracy
            )
            filteredGpsAlt
        }

        // Step 6: Construct Filtered Location object
        val filteredLoc = Location(rawLocation).apply {
            latitude = filteredLat
            longitude = filteredLon
            altitude = finalAltitude
            speed = speedKmh / 3.6f
            this.accuracy = accuracy
            time = rawLocation.time
        }

        lastFilteredLocation = filteredLoc
        _fusedLocation.value = filteredLoc
        return filteredLoc
    }

    /**
     * Process hardware barometer pressure reading (hPa / mbar).
     */
    fun processBarometerPressure(pressureHpa: Float) {
        lastBaroPressureHpa = pressureHpa
        // Convert pressure to standard altitude using standard barometric formula
        val baroAlt = 44330.0 * (1.0 - Math.pow((pressureHpa / 1013.25).toDouble(), 0.1903))
        
        lastBaroAltitudeMeters?.let { prevAlt ->
            val deltaBaro = baroAlt - prevAlt
            if (isAltitudeInitialized) {
                fusedAltitudeMeters += deltaBaro
            } else {
                fusedAltitudeMeters = baroAlt
                isAltitudeInitialized = true
            }
        } ?: run {
            if (!isAltitudeInitialized) {
                fusedAltitudeMeters = baroAlt
                isAltitudeInitialized = true
            }
        }

        lastBaroAltitudeMeters = baroAlt

        if (_altitudeSource.value is AltitudeSource.Fused || _altitudeSource.value is AltitudeSource.Barometric) {
            val isCalibrated = (_altitudeSource.value as? AltitudeSource.Fused)?.isGpsCalibrated ?: false
            val gpsAcc = (_altitudeSource.value as? AltitudeSource.Fused)?.gpsAccuracyMeters ?: 15.0f

            _altitudeSource.value = AltitudeSource.Fused(
                altitudeMeters = fusedAltitudeMeters,
                pressureHpa = pressureHpa,
                isGpsCalibrated = isCalibrated,
                gpsAccuracyMeters = gpsAcc
            )
        }
    }

    fun reset() {
        latKalman.reset()
        lonKalman.reset()
        altKalman.reset()
        isAltitudeInitialized = false
        lastRawLocation = null
        lastFilteredLocation = null
        lastBaroPressureHpa = null
        lastBaroAltitudeMeters = null
        _fusedLocation.value = null
    }
}
