package com.example.presentation.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteHistoryScreen(
    routes: List<RouteEntity>,
    onDeleteRoute: (Long) -> Unit,
    onExportGpx: (Long, (String) -> Unit) -> Unit,
    onGetRouteDetail: suspend (Long) -> RouteWithPoints? = { null },
    onLoadRouteOnMap: (Long) -> Unit = {}
) {
    val context = LocalContext.current
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp)
            .testTag("route_history_screen")
    ) {
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
                        text = "HISTORIAL DE RUTAS (${routes.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(routes, key = { it.id }) { route ->
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

                                Row {
                                    IconButton(
                                        onClick = {
                                            onExportGpx(route.id) { path ->
                                                Toast.makeText(context, "Exportado GPX: $path", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("export_gpx_${route.id}")
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = "Exportar GPX", tint = NeonCyan)
                                    }

                                    IconButton(
                                        onClick = { routeToDelete = route },
                                        modifier = Modifier.testTag("delete_route_${route.id}")
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar", tint = NeonRed)
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
                                        text = "${route.durationSeconds} s",
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
                                Column {
                                    Text("DESNIVEL +", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                    Text(
                                        text = "+${route.elevationGainMeters.toInt()} m",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen,
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
                        Text("${route.durationSeconds} seg", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    OutlinedButton(
                        onClick = {
                            onExportGpx(route.id) { path ->
                                Toast.makeText(context, "Exportado GPX: $path", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar GPX", color = NeonCyan)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
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
