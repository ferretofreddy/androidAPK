package com.example.presentation.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.presentation.viewmodel.MapDownloadViewModel
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MapDownloadScreen(
    downloadViewModel: MapDownloadViewModel,
    initialLatitude: Double = 0.0,
    initialLongitude: Double = 0.0,
    onBackClick: () -> Unit = {},
    onSelectToLoad: (File) -> Unit = {}
) {
    val context = LocalContext.current

    val regionSelection by downloadViewModel.regionSelection.collectAsState()
    val estimatedTileCount by downloadViewModel.estimatedTileCount.collectAsState()
    val estimatedSizeBytes by downloadViewModel.estimatedSizeBytes.collectAsState()
    val isNetworkAvailable by downloadViewModel.isNetworkAvailable.collectAsState()
    val errorMessage by downloadViewModel.errorMessage.collectAsState()

    var osmdroidMapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        downloadViewModel.checkConnectivity()
    }

    // Center bounding box around device location if bounds still have default Lima coordinates
    LaunchedEffect(initialLatitude, initialLongitude) {
        if (initialLatitude != 0.0 && initialLongitude != 0.0) {
            if (regionSelection.minLat == -12.05 && regionSelection.maxLat == -12.00 &&
                regionSelection.minLon == -77.05 && regionSelection.maxLon == -77.00
            ) {
                downloadViewModel.updateBounds(
                    minLat = initialLatitude - 0.03,
                    maxLat = initialLatitude + 0.03,
                    minLon = initialLongitude - 0.03,
                    maxLon = initialLongitude + 0.03
                )
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            downloadViewModel.clearErrorMessage()
        }
    }

    DisposableEffect(osmdroidMapView) {
        val map = osmdroidMapView
        map?.onResume()
        onDispose {
            map?.onPause()
            map?.onDetach()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitSurface)
    ) {
        // Upper Map Panel (occupies all remaining space)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        if (initialLatitude != 0.0 && initialLongitude != 0.0) {
                            controller.setZoom(15.0)
                            controller.setCenter(GeoPoint(initialLatitude, initialLongitude))
                        } else {
                            controller.setZoom(12.0)
                            controller.setCenter(GeoPoint(-12.046374, -77.042793)) // Lima default fallback
                        }
                        osmdroidMapView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    // Update bounding box polygon preview overlay
                    mapView.overlays.removeAll { it is Polygon }
                    val poly = Polygon().apply {
                        val pts = listOf(
                            GeoPoint(regionSelection.maxLat, regionSelection.minLon),
                            GeoPoint(regionSelection.maxLat, regionSelection.maxLon),
                            GeoPoint(regionSelection.minLat, regionSelection.maxLon),
                            GeoPoint(regionSelection.minLat, regionSelection.minLon)
                        )
                        points = pts
                        fillPaint.color = AndroidColor.argb(50, 0, 229, 255) // Semi-transparent NeonCyan
                        outlinePaint.color = AndroidColor.argb(255, 0, 229, 255)
                        outlinePaint.strokeWidth = 3f
                    }
                    mapView.overlays.add(poly)
                    mapView.invalidate()
                }
            )

            // Compact floating button: only MyLocation icon, bottom right
            IconButton(
                onClick = {
                    osmdroidMapView?.let { mapView ->
                        val box = mapView.boundingBox
                        downloadViewModel.updateBounds(
                            minLat = box.latSouth,
                            maxLat = box.latNorth,
                            minLon = box.lonWest,
                            maxLon = box.lonEast
                        )
                        Toast.makeText(context, "Bounding box fijado", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(NeonCyan, shape = CircleShape)
                    .testTag("capture_box_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Fijar Bounding Box",
                    tint = Color.Black
                )
            }
        }

        // Bottom Controls Panel (fixed height ~320dp)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = CockpitSurface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            border = BorderStroke(0.5.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top row: Back button (left) and Wifi / WifiOff icon (right)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = NeonCyan
                            )
                        }

                        IconButton(
                            onClick = { downloadViewModel.checkConnectivity() },
                            modifier = Modifier.testTag("refresh_network_button")
                        ) {
                            Icon(
                                imageVector = if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = "Estado Red",
                                tint = if (isNetworkAvailable) NeonGreen else NeonRed
                            )
                        }
                    }
                }

                // Map Name
                item {
                    OutlinedTextField(
                        value = regionSelection.name,
                        onValueChange = { downloadViewModel.updateName(it) },
                        label = { Text("Nombre del Mapa") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted
                        ),
                        singleLine = true
                    )
                }

                // Zoom range
                item {
                    Text(
                        text = "Rango de Zoom: ${regionSelection.zoomMin} - ${regionSelection.zoomMax}",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    RangeSlider(
                        value = regionSelection.zoomMin.toFloat()..regionSelection.zoomMax.toFloat(),
                        onValueChange = { range ->
                            downloadViewModel.updateZoomRange(
                                zoomMin = range.start.roundToInt().coerceIn(1, 18),
                                zoomMax = range.endInclusive.roundToInt().coerceIn(1, 18)
                            )
                        },
                        valueRange = 8f..17f,
                        steps = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zoom_range_slider")
                    )
                }

                // Estimated tiles and size
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Tiles Estimados", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "$estimatedTileCount tiles",
                                color = NeonOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Tamaño Estimado", color = TextSecondary, fontSize = 11.sp)
                            val mb = estimatedSizeBytes / (1024.0 * 1024.0)
                            Text(
                                text = String.format(Locale.US, "%.1f MB", mb),
                                color = if (mb > 150) NeonRed else NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    if (estimatedSizeBytes > 150 * 1024 * 1024) {
                        Text(
                            text = "⚠️ Tamaño elevado (>150MB). Considere reducir el área o rango de zoom.",
                            color = NeonRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Start download button
                item {
                    Button(
                        onClick = { downloadViewModel.startDownload() },
                        enabled = isNetworkAvailable && estimatedTileCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_download_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            disabledContainerColor = TextMuted
                        )
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Iniciar Descarga",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
