package com.example.presentation.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DownloadedMapEntity
import com.example.data.local.MapMarkerEntity
import com.example.data.local.RouteEntity
import com.example.domain.model.RouteWithPoints
import com.example.presentation.ui.components.MbtilesMapView
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ResourcesSection {
    MAPAS,
    RUTAS,
    WAYPOINTS
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteHistoryScreen(
    routes: List<RouteEntity>,
    downloadedMaps: List<DownloadedMapEntity> = emptyList(),
    markers: List<MapMarkerEntity> = emptyList(),
    visibleRouteIds: Set<Long> = emptySet(),
    onDeleteMap: (DownloadedMapEntity) -> Unit = {},
    onDeleteRoute: (Long) -> Unit = {},
    onToggleRouteVisibility: (Long) -> Unit = {},
    onToggleMarker: (Long) -> Unit = {},
    onDeleteMarker: (Long) -> Unit = {},
    onGetRouteDetail: suspend (Long) -> RouteWithPoints? = { null },
    onLoadRouteOnMap: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedSection by rememberSaveable { mutableStateOf(ResourcesSection.MAPAS) }
    var routeToDelete by remember { mutableStateOf<RouteEntity?>(null) }
    var selectedRouteDetail by remember { mutableStateOf<RouteWithPoints?>(null) }
    var selectedRouteIdToFetch by remember { mutableStateOf<Long?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }

    LaunchedEffect(selectedRouteIdToFetch) {
        val id = selectedRouteIdToFetch
        if (id != null) {
            selectedRouteDetail = onGetRouteDetail(id)
            selectedRouteIdToFetch = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("route_history_screen")
    ) {
        // Top 3 Tabs
        TabRow(
            selectedTabIndex = selectedSection.ordinal,
            containerColor = CockpitSurface,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSection.ordinal]),
                    color = NeonCyan
                )
            },
            divider = {
                HorizontalDivider(color = Color(0x33FFFFFF))
            }
        ) {
            ResourcesSection.entries.forEach { section ->
                val title = when (section) {
                    ResourcesSection.MAPAS -> "Mapas"
                    ResourcesSection.RUTAS -> "Rutas"
                    ResourcesSection.WAYPOINTS -> "WayPoints"
                }
                val isSelected = selectedSection == section
                Tab(
                    selected = isSelected,
                    onClick = { selectedSection = section },
                    text = {
                        Text(
                            text = title,
                            color = if (isSelected) NeonCyan else TextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            when (selectedSection) {
                ResourcesSection.MAPAS -> {
                    if (downloadedMaps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay mapas descargados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = "MAPAS (${downloadedMaps.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            items(downloadedMaps, key = { it.id }) { mapItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CockpitSurface, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Map,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = mapItem.name,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${mapItem.sizeBytes / (1024 * 1024)} MB · ${mapItem.totalTiles} tiles",
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            modifier = Modifier.padding(start = 26.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteMap(mapItem) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("delete_map_${mapItem.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = NeonRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ResourcesSection.RUTAS -> {
                    if (routes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.height(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No hay rutas grabadas aún",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Inicia una grabación desde el Panel Cockpit o Mapa para registrar tu track GPS y telemetría.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = "RUTAS (${routes.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            items(routes, key = { it.id }) { route ->
                                val isVisible = route.id in visibleRouteIds
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRouteIdToFetch = route.id
                                        }
                                        .testTag("route_item_${route.id}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                                    contentDescription = null,
                                                    tint = NeonGreen
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = route.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                    Text(
                                                        text = dateFormat.format(Date(route.startTimeTimestamp)),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextMuted
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { onToggleRouteVisibility(route.id) },
                                                    modifier = Modifier.testTag("toggle_visibility_${route.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = if (isVisible) "Ocultar en mapa" else "Mostrar en mapa",
                                                        tint = if (isVisible) NeonCyan else TextMuted
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { routeToDelete = route },
                                                    modifier = Modifier.testTag("delete_route_${route.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Eliminar",
                                                        tint = NeonRed
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = CockpitSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("DISTANCIA", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                                Text(
                                                    text = "${String.format(Locale.US, "%.2f", route.totalDistanceMeters / 1000.0)} km",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonOrange,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Column {
                                                Text("TIEMPO", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                                Text(
                                                    text = formatDuration(route.durationSeconds),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Column {
                                                Text("VEL. MEDIA", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", route.avgSpeedKmh)} km/h",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonCyan,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ResourcesSection.WAYPOINTS -> {
                    if (markers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay waypoints guardados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = "WAYPOINTS (${markers.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            items(markers, key = { it.id }) { marker ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CockpitSurface, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                                .background(Color(marker.colorArgb), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = marker.name,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = String.format(Locale.US, "%.6f, %.6f", marker.latitude, marker.longitude),
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onToggleMarker(marker.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("toggle_marker_${marker.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (marker.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (marker.isVisible) "Ocultar waypoint" else "Mostrar waypoint",
                                                tint = if (marker.isVisible) NeonCyan else TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteMarker(marker.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_marker_${marker.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = NeonRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Modal Bottom Sheet with Map Polyline & Full Metrics
    selectedRouteDetail?.let { detail ->
        val route = detail.route
        val points = detail.trackPoints

        val initialLat = points.firstOrNull()?.latitude ?: 0.0
        val initialLng = points.firstOrNull()?.longitude ?: 0.0

        ModalBottomSheet(
            onDismissRequest = { selectedRouteDetail = null },
            containerColor = CockpitSurface,
            modifier = Modifier.testTag("route_detail_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(route.startTimeTimestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Button(
                        onClick = {
                            onLoadRouteOnMap(route.id)
                            selectedRouteDetail = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver en Mapa", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Map Polyline Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    MbtilesMapView(
                        latitude = initialLat,
                        longitude = initialLng,
                        headingDegrees = 0f,
                        activeTrackPoints = emptyList(),
                        historicalTrackPoints = points,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("DISTANCIA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${String.format(Locale.US, "%.2f", route.totalDistanceMeters / 1000.0)} km", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column {
                        Text("DURACIÓN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(formatDuration(route.durationSeconds), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column {
                        Text("DESNIVEL +", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("+${route.elevationGainMeters.toInt()} m", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("VELOCIDAD MEDIA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${String.format(Locale.US, "%.1f", route.avgSpeedKmh)} km/h", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column {
                        Text("VELOCIDAD MÁXIMA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${String.format(Locale.US, "%.1f", route.maxSpeedKmh)} km/h", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column {
                        Text("PUNTOS TRACK", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${points.size}", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { selectedRouteDetail = null }) {
                        Text("Cerrar", color = TextMuted)
                    }
                }
            }
        }
    }

    // Confirm Delete Dialog
    routeToDelete?.let { targetRoute ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            containerColor = CockpitSurface,
            title = { Text("Eliminar Ruta", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas eliminar '${targetRoute.name}' y sus puntos del historial?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRoute(targetRoute.id)
                        routeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { routeToDelete = null }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }
}
