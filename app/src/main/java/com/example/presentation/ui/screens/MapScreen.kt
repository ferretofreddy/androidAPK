package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.domain.model.RouteRecordingState
import com.example.domain.model.RouteWithPoints
import com.example.presentation.ui.components.MbtilesMapView
import com.example.presentation.viewmodel.MapViewModel
import com.example.presentation.viewmodel.RouteHistoryViewModel
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.Locale

import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    latitude: Double,
    longitude: Double,
    headingDegrees: Float,
    gpsAccuracyMeters: Float = 0f,
    compassAccuracy: com.example.data.sensor.CompassAccuracy = com.example.data.sensor.CompassAccuracy.HIGH,
    isCalibrationNeeded: Boolean = false,
    mapViewModel: MapViewModel,
    historyViewModel: RouteHistoryViewModel,
    onOpenDownloadManager: () -> Unit = {}
) {
    val context = LocalContext.current

    val recordingState by mapViewModel.recordingState.collectAsState()
    val recordedDistanceMeters by mapViewModel.recordedDistanceMeters.collectAsState()
    val activeTrackPoints by mapViewModel.activeTrackPoints.collectAsState()
    val startLocation by mapViewModel.startLocation.collectAsState()
    val loadedHistoricalRoute by mapViewModel.loadedHistoricalRoute.collectAsState()
    val selectedMbtilesFile by mapViewModel.selectedMbtilesFile.collectAsState()
    val availableRoutes by historyViewModel.routesList.collectAsState()

    var mapStatusMessage by remember { mutableStateOf("Mapa offline osmdroid listo") }
    var showMbtilesSheet by remember { mutableStateOf(false) }
    var showLoadRouteSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }

    val availableMbtilesFiles = remember(showMbtilesSheet) {
        mapViewModel.mbTilesManager.listAvailableMbtilesFiles()
    }

    val gpsAccuracyColor = when {
        gpsAccuracyMeters <= 5f -> NeonGreen
        gpsAccuracyMeters <= 15f -> NeonCyan
        gpsAccuracyMeters <= 30f -> NeonOrange
        else -> NeonRed
    }

    val compassAccuracyColor = when (compassAccuracy) {
        com.example.data.sensor.CompassAccuracy.HIGH -> NeonGreen
        com.example.data.sensor.CompassAccuracy.MEDIUM -> NeonCyan
        com.example.data.sensor.CompassAccuracy.LOW -> NeonOrange
        com.example.data.sensor.CompassAccuracy.UNRELIABLE -> NeonRed
    }

    // Distance string calculation
    val distanceDisplayString = remember(recordingState, recordedDistanceMeters, startLocation, latitude, longitude) {
        if (recordingState is RouteRecordingState.Recording) {
            val dist = recordedDistanceMeters
            if (dist < 1000.0) {
                "${dist.toInt()} m"
            } else {
                String.format(Locale.US, "%.2f km", dist / 1000.0)
            }
        } else {
            "—"
        }
    }

    // Cardinal direction calculation
    val cardinalDirection = getCardinalDirection(headingDegrees)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("map_screen")
    ) {
        // Main Body: Offline osmdroid Map
        MbtilesMapView(
            latitude = latitude,
            longitude = longitude,
            headingDegrees = headingDegrees,
            activeTrackPoints = activeTrackPoints,
            historicalTrackPoints = loadedHistoricalRoute?.trackPoints ?: emptyList(),
            selectedMbtilesFile = selectedMbtilesFile,
            modifier = Modifier.fillMaxSize(),
            onMapReadyStatus = { msg -> mapStatusMessage = msg }
        )

        // Top Navigation Persistent Bar (High contrast solid overlay)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xEE121824)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter)
                .testTag("top_navigation_bar")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Position Coordinates & GNSS Precision
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "UBICACIÓN ACTUAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Text(
                            text = String.format(Locale.US, "%.6f°, %.6f°", latitude, longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = String.format(Locale.US, "GPS: ±%.1fm", gpsAccuracyMeters),
                            style = MaterialTheme.typography.labelSmall,
                            color = gpsAccuracyColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }

                    // True Heading & Compass Accuracy
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RUMBO VERDADERO",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Text(
                            text = String.format(Locale.US, "%03d° %s", headingDegrees.toInt(), cardinalDirection),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isCalibrationNeeded) "⚠️ CALIBRAR" else "Brújula: ${compassAccuracy.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCalibrationNeeded) NeonOrange else compassAccuracyColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Route Start Distance
                    Column {
                        Text(
                            text = "DISTANCIA DESDE INICIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Text(
                            text = distanceDisplayString,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (recordingState is RouteRecordingState.Recording) NeonGreen else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Loaded Route Badge / Active Recording Badge
                    if (loadedHistoricalRoute != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NeonOrange.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ruta: ${loadedHistoricalRoute?.route?.name}",
                                        color = NeonOrange,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { mapViewModel.clearHistoricalRoute() },
                                        modifier = Modifier.padding(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Quitar Ruta",
                                            tint = NeonOrange
                                        )
                                    }
                                }
                            }
                        }
                    } else if (recordingState is RouteRecordingState.Recording) {
                        Card(colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.2f))) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    tint = NeonRed
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "GRABANDO RUTA",
                                    color = NeonRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Controls over Map
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Button: Cargar Mapa Offline (.mbtiles)
            FloatingActionButton(
                onClick = { showMbtilesSheet = true },
                containerColor = CockpitSurface,
                contentColor = NeonCyan,
                modifier = Modifier.testTag("mbtiles_fab")
            ) {
                Icon(imageVector = Icons.Default.FolderZip, contentDescription = "Cargar Mapa MBTiles")
            }

            // Button: Cargar Ruta Histórica (Room)
            FloatingActionButton(
                onClick = { showLoadRouteSheet = true },
                containerColor = CockpitSurface,
                contentColor = NeonOrange,
                modifier = Modifier.testTag("load_route_fab")
            ) {
                Icon(imageVector = Icons.Default.History, contentDescription = "Cargar Ruta Histórica")
            }

            // Button: Detener y Guardar (Visible only while recording)
            AnimatedVisibility(visible = recordingState is RouteRecordingState.Recording) {
                Button(
                    onClick = {
                        routeNameInput = "Ruta Garmin ${System.currentTimeMillis() % 10000}"
                        showSaveDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = TextPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("stop_save_route_button")
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Detener y Guardar", fontWeight = FontWeight.Bold)
                }
            }

            // Button: Grabar Ruta / Estado Grabando
            Button(
                onClick = {
                    if (recordingState is RouteRecordingState.Idle || recordingState is RouteRecordingState.Saved) {
                        mapViewModel.startRecording()
                        Toast.makeText(context, "Iniciando grabación de ruta...", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recordingState is RouteRecordingState.Recording) NeonGreen else NeonCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.testTag("record_route_button")
            ) {
                val icon = if (recordingState is RouteRecordingState.Recording) Icons.Default.FiberManualRecord else Icons.Default.PlayArrow
                Icon(imageVector = icon, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (recordingState is RouteRecordingState.Recording) "Grabando Ruta..." else "Grabar Ruta",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Save Route Dialog
    if (showSaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Guardar Ruta Grabada", color = TextPrimary) },
            text = {
                Column {
                    Text("Ingresa un nombre para persistir el trackpoint en Room DB y exportar GPX:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = routeNameInput,
                        onValueChange = { routeNameInput = it },
                        label = { Text("Nombre de la Ruta") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor = NeonCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("route_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = routeNameInput.ifBlank { "Ruta Garmin" }
                        mapViewModel.stopAndSaveRecording(name) { routeId ->
                            Toast.makeText(context, "Ruta '$name' guardada exitosamente", Toast.LENGTH_SHORT).show()
                            // Option to export GPX immediately
                            historyViewModel.exportGpx(routeId) { file ->
                                if (file != null) {
                                    Toast.makeText(context, "GPX Exportado: ${file.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("Guardar", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
            containerColor = CockpitSurface
        )
    }

    // Modal Sheet: Select Local MBTiles File
    if (showMbtilesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMbtilesSheet = false },
            containerColor = CockpitSurface,
            modifier = Modifier.testTag("mbtiles_selection_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Cargar Mapa Offline MBTiles",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Directorio local: ${mapViewModel.mbTilesManager.getMbtilesDirectory().absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        showMbtilesSheet = false
                        onOpenDownloadManager()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("download_new_map_option_button")
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Descargar Nuevo Mapa", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                if (availableMbtilesFiles.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "No se encontraron archivos .mbtiles en el almacenamiento local.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Para renderizar mapas 100% offline sin red, copia archivos .mbtiles a la carpeta 'GarminDash_MBTiles'.",
                                color = NeonOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        items(availableMbtilesFiles) { file ->
                            Card(
                                onClick = {
                                    mapViewModel.selectMbtilesFile(file)
                                    showMbtilesSheet = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedMbtilesFile?.path == file.path) NeonCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = NeonCyan)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(file.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("${file.length() / (1024 * 1024)} MB", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showMbtilesSheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cerrar", color = NeonCyan)
                }
            }
        }
    }

    // Modal Sheet: Load Historical Route from Room
    if (showLoadRouteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLoadRouteSheet = false },
            containerColor = CockpitSurface,
            modifier = Modifier.testTag("load_route_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Seleccionar Ruta Histórica (Room DB)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dibuja la polilínea naranja sobre el mapa actual",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (availableRoutes.isEmpty()) {
                    Text("No hay rutas guardadas aún. Graba una ruta desde los controles del mapa.", color = TextSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn {
                        items(availableRoutes) { routeItem ->
                            Card(
                                onClick = {
                                    mapViewModel.loadHistoricalRoute(routeItem.id)
                                    showLoadRouteSheet = false
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(routeItem.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            "${String.format(Locale.US, "%.2f", routeItem.totalDistanceMeters / 1000.0)} km • ${routeItem.pointCount} puntos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonOrange
                                        )
                                    }
                                    Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = NeonOrange)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showLoadRouteSheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cerrar", color = NeonCyan)
                }
            }
        }
    }
}

private fun getCardinalDirection(degrees: Float): String {
    val normalized = (degrees % 360 + 360) % 360
    return when {
        normalized >= 337.5 || normalized < 22.5 -> "N"
        normalized >= 22.5 && normalized < 67.5 -> "NE"
        normalized >= 67.5 && normalized < 112.5 -> "E"
        normalized >= 112.5 && normalized < 157.5 -> "SE"
        normalized >= 157.5 && normalized < 202.5 -> "S"
        normalized >= 202.5 && normalized < 247.5 -> "SW"
        normalized >= 247.5 && normalized < 292.5 -> "W"
        else -> "NW"
    }
}
