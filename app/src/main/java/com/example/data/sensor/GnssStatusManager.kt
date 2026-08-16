package com.example.data.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data holder for GNSS satellite constellation status in GarminDash.
 */
data class GnssStatusData(
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val isSearching: Boolean = true
)

/**
 * GnssStatusManager - Hardware GNSS Satellite Tracking via LocationManager.registerGnssStatusCallback()
 *
 * Exposes real-time satellite count (used vs visible) and GPS search state
 * without relying exclusively on FusedLocationProviderClient (which omits satellite metrics).
 */
class GnssStatusManager(private val context: Context) {

    private val TAG = "GnssStatusManager"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gnssStatusData = MutableStateFlow(GnssStatusData())
    val gnssStatusData: StateFlow<GnssStatusData> = _gnssStatusData.asStateFlow()

    private var isCallbackRegistered = false

    private val gnssStatusCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val visible = status.satelliteCount
                var used = 0
                for (i in 0 until visible) {
                    if (status.usedInFix(i)) {
                        used++
                    }
                }
                _gnssStatusData.value = GnssStatusData(
                    satellitesUsed = used,
                    satellitesVisible = visible,
                    isSearching = used == 0
                )
            }

            override fun onStarted() {
                _gnssStatusData.value = _gnssStatusData.value.copy(isSearching = true)
            }

            override fun onStopped() {
                _gnssStatusData.value = _gnssStatusData.value.copy(isSearching = true)
            }
        }
    } else {
        null
    }

    @SuppressLint("MissingPermission")
    fun registerCallback() {
        if (isCallbackRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            if (hasLocationPermission()) {
                try {
                    locationManager?.registerGnssStatusCallback(
                        context.mainExecutor,
                        gnssStatusCallback
                    )
                    isCallbackRegistered = true
                    Log.d(TAG, "GnssStatusCallback successfully registered.")
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException registering GnssStatusCallback: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register GnssStatusCallback: ${e.message}")
                }
            } else {
                Log.w(TAG, "Location permissions missing. GnssStatusCallback postponed.")
            }
        }
    }

    fun unregisterCallback() {
        if (!isCallbackRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            try {
                locationManager?.unregisterGnssStatusCallback(gnssStatusCallback)
                isCallbackRegistered = false
                Log.d(TAG, "GnssStatusCallback unregistered.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister GnssStatusCallback: ${e.message}")
            }
        }
    }

    fun setSimulatedSatelliteData(used: Int = 14, visible: Int = 18) {
        _gnssStatusData.value = GnssStatusData(
            satellitesUsed = used,
            satellitesVisible = visible,
            isSearching = false
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
