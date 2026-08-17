package com.example.presentation.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import android.graphics.Paint
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.data.local.DownloadedMapEntity
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
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDownloadScreen(
    downloadViewModel: MapDownloadViewModel,
    onBackClick: () -> Unit = {},
    onSelectToLoad: (File) -> Unit = {}
) {
    val context = LocalContext.current

    val downloadedMaps by downloadViewModel.downloadedMaps.collectAsState()
    val regionSelection by downloadViewModel.regionSelection.collectAsState()
    val estimatedTileCount by downloadViewModel.estimatedTileCount.collectAsState()
    val estimatedSizeBytes by downloadViewModel.estimatedSizeBytes.collectAsState()
    val isNetworkAvailable by downloadViewModel.isNetworkAvailable.collectAsState()
    val errorMessage by downloadViewModel.errorMessage.collectAsState()

    var osmdroidMapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        downloadViewModel.checkConnectivity()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Descargar Mapas Offline",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Único módulo con acceso a red",
                            color = NeonOrange,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
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
                },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CockpitSurface
                )
            )
        },
        containerColor = CockpitSurface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNetworkAvailable) MaterialTheme.colorScheme.surfaceVariant else NeonRed.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isNetworkAvailable) NeonGreen else NeonRed
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isNetworkAvailable) "Conexión a internet activa" else "Sin conexión a internet",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isNetworkAvailable)
                                    "Listo para descargar tiles de mapas para almacenamiento local."
                                else
                                    "Se requiere internet para descargar nuevos mapas. Si ya posee archivos .mbtiles, la app funcionará 100% offline.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Region Selector Header
            item {
                Text(
                    text = "1. Seleccionar Región Geográfica",
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 16.sp
                )
                Text(
                    text = "Navegue en el mapa y presione 'Fijar Bounding Box' para seleccionar el área a descargar.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(12.0)
                                controller.setCenter(GeoPoint(-12.046374, -77.042793)) // Lima, Peru default center
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

                    Button(
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
                            .testTag("capture_box_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fijar Bounding Box", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Map Parameters
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Parámetros de Descarga",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 16.sp
                        )

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

                        Text(
                            text = "Rango de Zoom: ${regionSelection.zoomMin} - ${regionSelection.zoomMax}",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Tiles Estimados", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = "$estimatedTileCount tiles",
                                    color = NeonOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Tamaño Estimado", color = TextSecondary, fontSize = 12.sp)
                                val mb = estimatedSizeBytes / (1024.0 * 1024.0)
                                Text(
                                    text = String.format(Locale.US, "%.1f MB", mb),
                                    color = if (mb > 150) NeonRed else NeonGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        if (estimatedSizeBytes > 150 * 1024 * 1024) {
                            Text(
                                text = "⚠️ Tamaño elevado (>150MB). Considere reducir el área o rango de zoom.",
                                color = NeonRed,
                                fontSize = 11.sp
                            )
                        }

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
                                text = "Iniciar Descarga de Mapa",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Downloaded Maps Header & List
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. Mapas Offline Descargados",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${downloadedMaps.size} archivos",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (downloadedMaps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No se han descargado mapas offline aún. Configure un mapa en la sección superior para descargarlo.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(downloadedMaps, key = { it.id }) { mapItem ->
                    DownloadedMapCard(
                        mapItem = mapItem,
                        onUpdate = { downloadViewModel.updateMap(mapItem) },
                        onDelete = { downloadViewModel.deleteMap(mapItem) },
                        onSelectToLoad = {
                            val file = File(mapItem.filePath)
                            if (file.exists()) {
                                onSelectToLoad(file)
                                Toast.makeText(context, "Mapa '${mapItem.name}' cargado en visor", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Archivo no encontrado en disco", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedMapCard(
    mapItem: DownloadedMapEntity,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onSelectToLoad: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(mapItem.downloadDateTimestamp) { dateFormat.format(Date(mapItem.downloadDateTimestamp)) }
    val mbSize = remember(mapItem.sizeBytes) { mapItem.sizeBytes / (1024.0 * 1024.0) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloaded_map_card_${mapItem.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = if (mapItem.isCompleted) NeonGreen else NeonOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mapItem.name,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }

                Text(
                    text = if (mapItem.isCompleted) "Completado" else "En Progreso (${mapItem.downloadedTiles}/${mapItem.totalTiles})",
                    color = if (mapItem.isCompleted) NeonGreen else NeonOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Zoom: ${mapItem.zoomMin}-${mapItem.zoomMax} • ${mapItem.totalTiles} tiles • ${String.format(Locale.US, "%.1f", mbSize)} MB",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Text(
                text = "Fecha: $dateStr",
                color = TextMuted,
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onUpdate,
                    modifier = Modifier.testTag("update_map_button_${mapItem.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Actualizar", color = NeonCyan, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_map_button_${mapItem.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = NeonRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar", color = NeonRed, fontSize = 12.sp)
                }

                if (mapItem.isCompleted && mapItem.filePath.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSelectToLoad,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.testTag("select_map_button_${mapItem.id}")
                    ) {
                        Text("Cargar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
