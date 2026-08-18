package com.example.presentation.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.repository.MBTilesManager
import com.example.presentation.viewmodel.MapDownloadViewModel
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
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
    val mbTilesManager = remember { MBTilesManager(context) }

    val regionSelection by downloadViewModel.regionSelection.collectAsState()
    val estimatedTileCount by downloadViewModel.estimatedTileCount.collectAsState()
    val estimatedSizeBytes by downloadViewModel.estimatedSizeBytes.collectAsState()
    val isNetworkAvailable by downloadViewModel.isNetworkAvailable.collectAsState()
    val isDownloading by downloadViewModel.isDownloading.collectAsState()
    val errorMessage by downloadViewModel.errorMessage.collectAsState()

    var osmdroidMapView by remember { mutableStateOf<MapView?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        downloadViewModel.checkConnectivity()
    }

    LaunchedEffect(osmdroidMapView) {
        osmdroidMapView?.let { mapView ->
            mbTilesManager.setupOfflineMapView(mapView, null)
        }
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
        // Upper Map Panel (Game Boy screen bezel frame)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0B0F16)
            ),
            border = BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)),
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
                        .padding(8.dp)
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
        }

        // Subtle divider line between screen bezel and bottom controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x33FFFFFF))
        )

        // Bottom Controls Panel (Flat body of CockpitSurface, auto height, no scroll)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CockpitSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Map Name field with clickable trailingIcon for network status
            OutlinedTextField(
                value = regionSelection.name,
                onValueChange = { downloadViewModel.updateName(it) },
                label = { Text("Nombre del Mapa", fontSize = 12.sp) },
                textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_name_input"),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { downloadViewModel.checkConnectivity() }
                            .testTag("refresh_network_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isNetworkAvailable) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Conectado",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Sin conexión",
                                tint = NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "!",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = TextMuted,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = TextMuted
                ),
                singleLine = true
            )

            // Row 2: Zoom range label & RangeSlider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Rango de Zoom: ${regionSelection.zoomMin} - ${regionSelection.zoomMax}",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
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
                        .heightIn(max = 38.dp)
                        .testTag("zoom_range_slider")
                )
            }

            // Row 3: Estimated tiles and size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tiles Estimados", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "$estimatedTileCount tiles",
                        color = NeonOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Tamaño Estimado", color = TextSecondary, fontSize = 11.sp)
                    val mb = estimatedSizeBytes / (1024.0 * 1024.0)
                    Text(
                        text = String.format(Locale.US, "%.1f MB", mb),
                        color = if (mb > 150) NeonRed else NeonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (estimatedSizeBytes > 150 * 1024 * 1024) {
                Text(
                    text = "⚠️ Tamaño elevado (>150MB). Considere reducir el área o rango de zoom.",
                    color = NeonRed,
                    fontSize = 10.sp
                )
            }

            // Row 4: Action buttons (Back button weight 1f, Download button weight 2f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("back_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CockpitSurface,
                        contentColor = NeonCyan
                    ),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Volver",
                        color = NeonCyan,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = { showConfirmationDialog = true },
                    enabled = isNetworkAvailable && estimatedTileCount > 0 && !isDownloading,
                    modifier = Modifier
                        .weight(2f)
                        .testTag("start_download_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        disabledContainerColor = TextMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDownloading) "Descargando..." else "Iniciar Descarga",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (showConfirmationDialog) {
        val mb = estimatedSizeBytes / (1024.0 * 1024.0)
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            containerColor = CockpitSurface,
            title = {
                Text(
                    text = "Confirmar Descarga",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Desea iniciar la descarga del área seleccionada?",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• Nombre: ${regionSelection.name}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• Tiles estimados: $estimatedTileCount tiles",
                        color = NeonOrange,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• Tamaño estimado: ${String.format(Locale.US, "%.1f MB", mb)}",
                        color = if (mb > 150) NeonRed else NeonGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        downloadViewModel.startDownload()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Confirmar Descarga", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmationDialog = false }
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
