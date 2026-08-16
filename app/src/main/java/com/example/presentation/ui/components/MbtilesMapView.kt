package com.example.presentation.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.TrackPointEntity
import com.example.data.repository.MBTilesManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun MbtilesMapView(
    latitude: Double,
    longitude: Double,
    headingDegrees: Float,
    activeTrackPoints: List<TrackPointEntity> = emptyList(),
    historicalTrackPoints: List<TrackPointEntity> = emptyList(),
    selectedMbtilesFile: File? = null,
    modifier: Modifier = Modifier,
    onMapReadyStatus: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val mbTilesManager = remember { MBTilesManager(context) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            setBuiltInZoomControls(false)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            try {
                mapView.onPause()
                mapView.onDetach()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    LaunchedEffect(selectedMbtilesFile) {
        val statusMsg = mbTilesManager.setupOfflineMapView(mapView, selectedMbtilesFile)
        onMapReadyStatus?.invoke(statusMsg)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxSize()
            .testTag("mbtiles_map_view"),
        update = { view ->
            val centerGeo = GeoPoint(
                if (latitude != 0.0) latitude else 40.416775,
                if (longitude != 0.0) longitude else -3.703790
            )

            view.overlays.clear()

            // 1. Draw Historical Route Polyline (Orange)
            if (historicalTrackPoints.isNotEmpty()) {
                val historicalPolyline = Polyline().apply {
                    outlinePaint.color = AndroidColor.parseColor("#F97316") // Neon / Sleek Orange
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                val geoPoints = historicalTrackPoints.map { GeoPoint(it.latitude, it.longitude) }
                historicalPolyline.setPoints(geoPoints)
                view.overlays.add(historicalPolyline)
            }

            // 2. Draw Active Recorded Route Polyline (Cyan)
            if (activeTrackPoints.isNotEmpty()) {
                val activePolyline = Polyline().apply {
                    outlinePaint.color = AndroidColor.parseColor("#00F0FF") // Neon Cyan
                    outlinePaint.strokeWidth = 11f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                val geoPoints = activeTrackPoints.map { GeoPoint(it.latitude, it.longitude) }
                activePolyline.setPoints(geoPoints)
                view.overlays.add(activePolyline)
            }

            // 3. Current Location Marker with Heading Arrow Indicator
            try {
                val locationMarker = Marker(view).apply {
                    position = centerGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Ubicación Actual"
                    snippet = "Lat: ${String.format("%.6f", latitude)}, Lon: ${String.format("%.6f", longitude)}"
                    rotation = headingDegrees
                }
                view.overlays.add(locationMarker)
            } catch (e: Exception) {
                // Ignore marker creation if view is detaching
            }

            // Center camera
            if (latitude != 0.0 && longitude != 0.0) {
                view.controller.setCenter(centerGeo)
            }
            view.invalidate()
        }
    )
}
