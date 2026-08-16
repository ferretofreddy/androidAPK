package com.example.data.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.example.domain.model.AltitudeSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

@SuppressLint("MissingPermission")
class SensorManager(private val context: Context) : SensorEventListener, LocationListener {

    private val TAG = "GarminSensorManager"

    private val androidSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager
    private val rotationVectorSensor = androidSensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val pressureSensor = androidSensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accelerometerSensor = androidSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor = androidSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Barometer System Feature Verification
    val hasBarometerFeature: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)

    // Output Telemetry State
    private val _headingDegrees = MutableStateFlow(0f)
    val headingDegrees: StateFlow<Float> = _headingDegrees.asStateFlow()

    private val _pitchDegrees = MutableStateFlow(0f)
    val pitchDegrees: StateFlow<Float> = _pitchDegrees.asStateFlow()

    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private val _pressureHpa = MutableStateFlow(1013.25f)
    val pressureHpa: StateFlow<Float> = _pressureHpa.asStateFlow()

    private val _barometricAltitude = MutableStateFlow(0.0)
    val barometricAltitude: StateFlow<Double> = _barometricAltitude.asStateFlow()

    private val _altitudeSource = MutableStateFlow<AltitudeSource>(AltitudeSource.GpsDerived(0.0, 0f))
    val altitudeSource: StateFlow<AltitudeSource> = _altitudeSource.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isSensorSimulating = MutableStateFlow(false)
    val isSensorSimulating: StateFlow<Boolean> = _isSensorSimulating.asStateFlow()

    val locationFusionManager = LocationFusionManager()
    val compassCalibrationManager = CompassCalibrationManager()
    val gnssStatusManager = GnssStatusManager(context)

    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var simJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _samplingIntervalSeconds = MutableStateFlow(2)
    val samplingIntervalSeconds: StateFlow<Int> = _samplingIntervalSeconds.asStateFlow()

    private fun hasLocationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun updateSamplingInterval(intervalSeconds: Int) {
        val safeInterval = intervalSeconds.coerceIn(1, 10)
        _samplingIntervalSeconds.value = safeInterval

        // Unregister existing listeners to update rates
        try {
            androidSensorManager?.unregisterListener(this)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore
        }

        val sensorDelay = if (safeInterval <= 1) AndroidSensorManager.SENSOR_DELAY_UI else AndroidSensorManager.SENSOR_DELAY_NORMAL

        // Re-register hardware sensors with target delay
        try {
            if (rotationVectorSensor != null) {
                androidSensorManager?.registerListener(this, rotationVectorSensor, sensorDelay)
            } else {
                accelerometerSensor?.let { androidSensorManager?.registerListener(this, it, sensorDelay) }
                magneticSensor?.let { androidSensorManager?.registerListener(this, it, sensorDelay) }
            }

            if (hasBarometerFeature && pressureSensor != null) {
                androidSensorManager?.registerListener(this, pressureSensor, sensorDelay)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sensor registration failed: ${e.message}")
        }

        // Re-register GNSS with configured sampling interval and priority
        if (hasLocationPermission()) {
            gnssStatusManager.registerCallback()
            try {
                // Estrategia "Fix Rápido + Continuo":
                // 1. Solicitar primer fix de forma proactiva e inmediata
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null && _currentLocation.value == null) {
                            Log.d(TAG, "Fix rápido inicial obtenido con éxito.")
                            handleNewLocation(loc)
                        }
                    }

                // 2. Transmisión continua para el Cockpit / Mapa
                val intervalMs = safeInterval * 1000L
                val priority = if (safeInterval >= 5) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_HIGH_ACCURACY
                val locationRequest = LocationRequest.Builder(priority, intervalMs)
                    .setMinUpdateIntervalMillis(intervalMs / 2)
                    .build()

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException requesting fused location updates: ${e.message}")
                startFallbackLocationManager()
            } catch (e: Exception) {
                Log.w(TAG, "Re-registering location updates failed: ${e.message}")
                startFallbackLocationManager()
            }
        } else {
            Log.w(TAG, "Location permissions not yet granted. Waiting for permission approval.")
        }
    }

    fun startSensorsAndLocation() {
        updateSamplingInterval(_samplingIntervalSeconds.value)
        startFallbackSimulationIfNeeded()
    }

    fun stopSensorsAndLocation() {
        simJob?.cancel()
        simJob = null
        try {
            gnssStatusManager.unregisterCallback()
            androidSensorManager?.unregisterListener(this)
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sensors: ${e.message}")
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc)
        }
    }

    private fun startFallbackLocationManager() {
        if (!hasLocationPermission()) return
        try {
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            } else if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, this)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in LocationManager fallback: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "LocationManager fallback failed: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        handleNewLocation(location)
    }

    private fun handleNewLocation(location: Location) {
        val filtered = locationFusionManager.processLocation(location, hasBarometerFeature)
        if (filtered != null) {
            _currentLocation.value = filtered
            _barometricAltitude.value = locationFusionManager.altitudeSource.value.altitudeMeters
            _altitudeSource.value = locationFusionManager.altitudeSource.value
            compassCalibrationManager.updateLocationForDeclination(filtered)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                AndroidSensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                AndroidSensorManager.getOrientation(rotationMatrix, orientationValues)

                var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                val trueHeading = compassCalibrationManager.processRawAzimuth(azimuth)
                val pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                _headingDegrees.value = trueHeading
                _pitchDegrees.value = pitch
                _rollDegrees.value = roll
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                hasAccelerometer = true
                updateOrientationFromAccMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                hasMagnetometer = true
                updateOrientationFromAccMag()
            }
            Sensor.TYPE_PRESSURE -> {
                if (hasBarometerFeature) {
                    val pressure = event.values[0]
                    _pressureHpa.value = pressure
                    locationFusionManager.processBarometerPressure(pressure)
                    _barometricAltitude.value = locationFusionManager.altitudeSource.value.altitudeMeters
                    _altitudeSource.value = locationFusionManager.altitudeSource.value
                }
            }
        }
    }

    private fun updateOrientationFromAccMag() {
        if (hasAccelerometer && hasMagnetometer && rotationVectorSensor == null) {
            if (AndroidSensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
                AndroidSensorManager.getOrientation(rotationMatrix, orientationValues)
                var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                val trueHeading = compassCalibrationManager.processRawAzimuth(azimuth)
                val pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                _headingDegrees.value = trueHeading
                _pitchDegrees.value = pitch
                _rollDegrees.value = roll
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor?.let { compassCalibrationManager.onSensorAccuracyChanged(it.type, accuracy) }
    }

    private fun startFallbackSimulationIfNeeded() {
        simJob?.cancel()
        simJob = scope.launch {
            delay(1500)
            var mockLat = 40.416775
            var mockLon = -3.703790
            var mockAlt = 25.0
            var step = 0

            while (true) {
                if (_currentLocation.value == null) {
                    _isSensorSimulating.value = true
                    step++
                    mockLat += 0.00015 * Math.cos(step * 0.1)
                    mockLon += 0.00015 * Math.sin(step * 0.1)
                    mockAlt += Math.sin(step * 0.2) * 1.5

                    val mockLocation = Location("SimulatedGPS").apply {
                        latitude = mockLat
                        longitude = mockLon
                        altitude = mockAlt
                        speed = (12.5f + Math.sin(step * 0.3) * 3.5).toFloat() / 3.6f
                        accuracy = 3.5f
                        time = System.currentTimeMillis()
                    }
                    handleNewLocation(mockLocation)
                    gnssStatusManager.setSimulatedSatelliteData(14, 18)

                    if (pressureSensor == null || !hasBarometerFeature) {
                        val p = (1013.25f - (mockAlt / 8.5)).toFloat()
                        _pressureHpa.value = p
                        _barometricAltitude.value = mockAlt
                    }
                } else {
                    _isSensorSimulating.value = false
                }

                if (rotationVectorSensor == null) {
                    step++
                    _headingDegrees.value = ((step * 4) % 360).toFloat()
                    _pitchDegrees.value = (Math.sin(step * 0.1) * 15.0).toFloat()
                    _rollDegrees.value = (Math.cos(step * 0.1) * 20.0).toFloat()
                }

                delay(1000)
            }
        }
    }
}
