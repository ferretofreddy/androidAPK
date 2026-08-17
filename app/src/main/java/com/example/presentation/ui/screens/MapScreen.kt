package com.example.presentation.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val markers by mapViewModel.markers.collectAsState()

    val recordingBlinkTransition = rememberInfiniteTransition(label = "recording_blink")
    val recordingDotAlpha by recordingBlinkTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_dot_alpha"
    )

    var mapStatusMessage by remember { mutableStateOf("Mapa offline osmdroid listo") }
    var cameraFollow by rememberSaveable { mutableStateOf(true) }
    var mapRotatesWithHeading by rememberSaveable { mutableStateOf(false) }
    var topBarExpanded by rememberSaveable { mutableStateOf(false) }
    var showMbtilesSheet by remember { mutableStateOf(false) }
    var showLoadRouteSheet by remember { mutableStateOf(false) }
    var showMarkersSheet by remember { mutableStateOf(false) }
    var showCreateMarkerSheet by remember { mutableStateOf(false) }
    var pendingMarkerLat by remember { mutableStateOf<Double?>(null) }
    var pendingMarkerLon by remember { mutableStateOf<Double?>(null) }
    var markerNameInput by remember { mutableStateOf("") }
    var selectedMarkerColorArgb by remember { mutableStateOf(0xFF4CAF50.toInt()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
    var dragAccumulated by remember { mutableStateOf(0f) }
    val topBarDraggableState = rememberDraggableState { delta ->
        dragAccumulated += delta
        if (dragAccumulated > dragThresholdPx) {
            topBarExpanded = true
            dragAccumulated = 0f
        } else if (dragAccumulated < -dragThresholdPx) {
            topBarExpanded = false
            dragAccumulated = 0f
        }
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (topBarExpanded) 180f else 0f,
        label = "topBarArrowRotation"
    )

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
            markers = markers.filter { it.isVisible },
            selectedMbtilesFile = selectedMbtilesFile,
            cameraFollowsLocation = cameraFollow,
            rotateWithHeading = mapRotatesWithHeading,
            onUserPan = { cameraFollow = false },
            onMapLongPress = { lat, lon ->
                pendingMarkerLat = lat
                pendingMarkerLon = lon
                markerNameInput = ""
                showCreateMarkerSheet = true
            },
            modifier = Modifier.fillMaxSize(),
            onMapReadyStatus = { msg -> mapStatusMessage = msg }
        )

        // Top Navigation Persistent Bar ("Persiana" desplegable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC000000),
                            Color(0x33000000),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x8C121824)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .draggable(
                        state = topBarDraggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { dragAccumulated = 0f }
                    )
                    .testTag("top_navigation_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    // Tap area on the persistent information header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { topBarExpanded = !topBarExpanded }
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
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = String.format(Locale.US, "%.6f°, %.6f°", latitude, longitude),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = String.format(Locale.US, "GPS: ±%.1fm", gpsAccuracyMeters),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = gpsAccuracyColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            }

                            // True Heading & Compass Accuracy
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RUMBO VERDADERO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = String.format(Locale.US, "%03d° %s", headingDegrees.toInt(), cardinalDirection),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isCalibrationNeeded) "⚠️ CALIBRAR" else "Brújula: ${compassAccuracy.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCalibrationNeeded) NeonOrange else compassAccuracyColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

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
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = distanceDisplayString,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (recordingState is RouteRecordingState.Recording) NeonGreen else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Loaded Route Badge / Active Recording Blinking Indicator
                            if (loadedHistoricalRoute != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = NeonOrange.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, NeonOrange.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Ruta: ${loadedHistoricalRoute?.route?.name}",
                                                color = NeonOrange,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
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
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .alpha(recordingDotAlpha)
                                        .background(NeonGreen, CircleShape)
                                )
                            }
                        }
                    }

                    // Collapsible Map Options Section
                    AnimatedVisibility(visible = topBarExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Option 1: MBTiles
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CockpitSurface.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMbtilesSheet = true
                                        topBarExpanded = false
                                    }
                                    .testTag("option_mbtiles")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                    Text(
                                        text = "Cargar / Descargar Mapa MBTiles",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Option 2: Route History
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CockpitSurface.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showLoadRouteSheet = true
                                        topBarExpanded = false
                                    }
                                    .testTag("option_route_history")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = NeonOrange
                                    )
                                    Text(
                                        text = "Historial de Rutas",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Option 3: Marcadores
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CockpitSurface.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMarkersSheet = true
                                        topBarExpanded = false
                                    }
                                    .testTag("option_markers")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = NeonGreen
                                    )
                                    Text(
                                        text = "Marcadores",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Option 4: Rotar con el rumbo
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CockpitSurface.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("option_rotate_heading")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                            contentDescription = null,
                                            tint = NeonCyan
                                        )
                                        Text(
                                            text = "Rotar con el rumbo",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Switch(
                                        checked = mapRotatesWithHeading,
                                        onCheckedChange = { mapRotatesWithHeading = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = NeonCyan,
                                            uncheckedThumbColor = TextMuted,
                                            uncheckedTrackColor = CockpitSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Handle Row at the Bottom of the Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { topBarExpanded = !topBarExpanded }
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (topBarExpanded) "Colapsar menú" else "Desplegar opciones",
                            tint = TextMuted,
                            modifier = Modifier.rotate(arrowRotation)
                        )
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
            // Button: Toggle Seguir / Explorar Ubicación
            FloatingActionButton(
                onClick = { cameraFollow = true },
                containerColor = if (cameraFollow) NeonCyan else CockpitSurface,
                contentColor = if (cameraFollow) Color.Black else TextPrimary,
                modifier = Modifier.testTag("center_my_location_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Centrar y seguir mi ubicación"
                )
            }

            // Button: Detener y Guardar (Visible only while recording, compact FAB without text)
            AnimatedVisibility(visible = recordingState is RouteRecordingState.Recording) {
                FloatingActionButton(
                    onClick = {
                        routeNameInput = "Ruta Garmin ${System.currentTimeMillis() % 10000}"
                        showSaveDialog = true
                    },
                    containerColor = NeonRed,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("stop_save_route_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Detener y Guardar Ruta",
                        tint = Color.White
                    )
                }
            }

            // Button: Grabar Ruta (Compact FAB without text)
            FloatingActionButton(
                onClick = {
                    if (recordingState is RouteRecordingState.Idle || recordingState is RouteRecordingState.Saved) {
                        mapViewModel.startRecording()
                        Toast.makeText(context, "Iniciando grabación de ruta...", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = NeonCyan,
                contentColor = Color.White,
                modifier = Modifier.testTag("record_route_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = "Grabar Ruta",
                    tint = Color.White
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

    // Modal Sheet: Marcadores (Waypoints)
    if (showMarkersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMarkersSheet = false },
            containerColor = CockpitSurface,
            modifier = Modifier.testTag("markers_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Marcadores y Waypoints",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Puntos de interés guardados en el mapa",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        pendingMarkerLat = latitude
                        pendingMarkerLon = longitude
                        markerNameInput = ""
                        showCreateMarkerSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("new_marker_button")
                ) {
                    Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nuevo Marcador", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                if (markers.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "No hay marcadores guardados",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pulsa 'Nuevo Marcador' o mantén presionado cualquier punto del mapa para guardar un waypoint.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(markers, key = { it.id }) { markerItem ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color(markerItem.colorArgb), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = markerItem.name,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.5f°, %.5f°", markerItem.latitude, markerItem.longitude),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { mapViewModel.toggleMarkerVisibility(markerItem.id) }
                                        ) {
                                            Icon(
                                                imageVector = if (markerItem.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (markerItem.isVisible) "Ocultar marcador" else "Mostrar marcador",
                                                tint = if (markerItem.isVisible) NeonCyan else TextMuted
                                            )
                                        }
                                        IconButton(
                                            onClick = { mapViewModel.deleteMarker(markerItem.id) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar marcador",
                                                tint = NeonRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showMarkersSheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cerrar", color = NeonCyan)
                }
            }
        }
    }

    // Modal Sheet: Nuevo Marcador (Creación / Waypoint)
    if (showCreateMarkerSheet) {
        val markerColors = listOf(
            0xFF4CAF50.toInt(), // verde
            0xFF00F0FF.toInt(), // cian
            0xFFF97316.toInt(), // naranja
            0xFFEF4444.toInt(), // rojo
            0xFFFFEB3B.toInt(), // amarillo
            0xFF9C27B0.toInt()  // morado
        )

        val targetLat = pendingMarkerLat ?: latitude
        val targetLon = pendingMarkerLon ?: longitude
        val isCurrentLocation = targetLat == latitude && targetLon == longitude

        ModalBottomSheet(
            onDismissRequest = {
                showCreateMarkerSheet = false
                pendingMarkerLat = null
                pendingMarkerLon = null
                markerNameInput = ""
            },
            containerColor = CockpitSurface,
            modifier = Modifier.testTag("create_marker_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nuevo Marcador",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isCurrentLocation) "Ubicación actual"
                           else String.format(Locale.US, "Punto seleccionado: %.6f, %.6f", targetLat, targetLon),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentLocation) NeonCyan else NeonOrange,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = markerNameInput,
                    onValueChange = { markerNameInput = it },
                    label = { Text("Nombre del Marcador") },
                    placeholder = { Text("Ej. Campamento, Mirador, Cruce...") },
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
                        .testTag("marker_name_input")
                )

                Text(
                    text = "Color del Marcador",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    markerColors.forEach { colorArgb ->
                        val isSelected = selectedMarkerColorArgb == colorArgb
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(colorArgb), CircleShape)
                                .then(
                                    if (isSelected) Modifier.border(BorderStroke(2.dp, Color.White), CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedMarkerColorArgb = colorArgb }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            showCreateMarkerSheet = false
                            pendingMarkerLat = null
                            pendingMarkerLon = null
                            markerNameInput = ""
                        }
                    ) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val name = markerNameInput.ifBlank { "Marcador" }
                            mapViewModel.addMarker(
                                name = name,
                                latitude = targetLat,
                                longitude = targetLon,
                                colorArgb = selectedMarkerColorArgb
                            )
                            Toast.makeText(context, "Marcador '$name' guardado", Toast.LENGTH_SHORT).show()
                            showCreateMarkerSheet = false
                            showMarkersSheet = false
                            pendingMarkerLat = null
                            pendingMarkerLon = null
                            markerNameInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Text("Guardar", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
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
