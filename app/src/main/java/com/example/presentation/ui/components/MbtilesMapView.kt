package com.example.presentation.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.MapMarkerEntity
import com.example.data.local.TrackPointEntity
import com.example.data.repository.MBTilesManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun MbtilesMapView(
    latitude: Double,
    longitude: Double,
    headingDegrees: Float,
    activeTrackPoints: List<TrackPointEntity> = emptyList(),
    historicalTrackPoints: List<TrackPointEntity> = emptyList(),
    markers: List<MapMarkerEntity> = emptyList(),
    selectedMbtilesFile: File? = null,
    cameraFollowsLocation: Boolean = true,
    rotateWithHeading: Boolean = false,
    onUserPan: (() -> Unit)? = null,
    onMapLongPress: ((lat: Double, lon: Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onMapReadyStatus: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val mbTilesManager = remember { MBTilesManager(context) }
    val currentOnUserPan by rememberUpdatedState(onUserPan)
    val currentOnMapLongPress by rememberUpdatedState(onMapLongPress)
    val isProgrammaticCentering = remember { AtomicBoolean(false) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    DisposableEffect(mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (!isProgrammaticCentering.get()) {
                    currentOnUserPan?.invoke()
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                return false
            }
        }
        mapView.addMapListener(mapListener)
        mapView.onResume()
        onDispose {
            try {
                mapView.removeMapListener(mapListener)
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

            // 0. Map Events Overlay (Long Press detection)
            val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        currentOnMapLongPress?.invoke(p.latitude, p.longitude)
                        return true
                    }
                    return false
                }
            })
            view.overlays.add(mapEventsOverlay)

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

            // 3. Current Location Marker with Teardrop Icon and Heading Indicator
            try {
                val locationIcon = ContextCompat.getDrawable(view.context, R.drawable.ic_current_location)
                val locationMarker = Marker(view).apply {
                    position = centerGeo
                    if (locationIcon != null) {
                        icon = locationIcon
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Ubicación Actual"
                    snippet = "Lat: ${String.format("%.6f", latitude)}, Lon: ${String.format("%.6f", longitude)}"
                    rotation = if (rotateWithHeading) 0f else headingDegrees
                }
                view.overlays.add(locationMarker)
            } catch (e: Exception) {
                // Ignore marker creation if view is detaching
            }

            // 4. Custom Waypoints / Map Markers
            val density = view.context.resources.displayMetrics.density
            val sizePx = (22 * density).toInt().coerceAtLeast(1)
            val strokePx = (2 * density).toInt().coerceAtLeast(1)

            markers.filter { it.isVisible }.forEach { markerEntity ->
                try {
                    val fillShape = ShapeDrawable(OvalShape()).apply {
                        paint.isAntiAlias = true
                        paint.color = markerEntity.colorArgb
                        paint.style = Paint.Style.FILL
                        intrinsicWidth = sizePx
                        intrinsicHeight = sizePx
                    }
                    val strokeShape = ShapeDrawable(OvalShape()).apply {
                        paint.isAntiAlias = true
                        paint.color = AndroidColor.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = strokePx.toFloat()
                        intrinsicWidth = sizePx
                        intrinsicHeight = sizePx
                    }
                    val markerIcon = LayerDrawable(arrayOf(fillShape, strokeShape)).apply {
                        setBounds(0, 0, sizePx, sizePx)
                    }

                    val markerOverlay = Marker(view).apply {
                        position = GeoPoint(markerEntity.latitude, markerEntity.longitude)
                        title = markerEntity.name
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = markerIcon
                    }
                    view.overlays.add(markerOverlay)
                } catch (e: Exception) {
                    // Ignore individual marker rendering errors
                }
            }

            // Orientation: Garmin course-up mode or North-up
            view.setMapOrientation(if (rotateWithHeading) -headingDegrees else 0f)

            // Center camera only when cameraFollowsLocation is true
            if (cameraFollowsLocation && latitude != 0.0 && longitude != 0.0) {
                isProgrammaticCentering.set(true)
                view.controller.setCenter(centerGeo)
                view.post {
                    isProgrammaticCentering.set(false)
                }
            }
            view.invalidate()
        }
    )
}
