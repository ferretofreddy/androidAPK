package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.GarminDashDatabase
import com.example.data.repository.RouteRepository
import com.example.data.sensor.SensorManager
import com.example.presentation.ui.screens.DashboardScreen
import com.example.presentation.ui.screens.MapDownloadScreen
import com.example.presentation.ui.screens.MapScreen
import com.example.presentation.ui.screens.RouteHistoryScreen
import com.example.presentation.viewmodel.CockpitViewModel
import com.example.presentation.viewmodel.MapDownloadViewModel
import com.example.presentation.viewmodel.MapViewModel
import com.example.presentation.viewmodel.RouteHistoryViewModel
import com.example.ui.theme.GarminDashTheme
import com.example.ui.theme.TextMuted

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private lateinit var routeRepository: RouteRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            Toast.makeText(this, "Permisos de ubicación concedidos. Conectando GPS...", Toast.LENGTH_SHORT).show()
            sensorManager.startSensorsAndLocation()
        } else {
            Toast.makeText(this, "Navegación sin GPS activo. Puedes conceder permisos desde los ajustes de Android.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = SensorManager(applicationContext)
        sensorManager.startSensorsAndLocation()

        val database = GarminDashDatabase.getDatabase(applicationContext)
        routeRepository = RouteRepository(applicationContext, database.routeDao())

        requestAppPermissions()

        setContent {
            GarminDashTheme {
                val cockpitViewModel: CockpitViewModel = viewModel(
                    factory = CockpitViewModel.Factory(
                        context = applicationContext,
                        sensorManager = sensorManager,
                        routeRepository = routeRepository
                    )
                )

                val mapViewModel: MapViewModel = viewModel(
                    factory = MapViewModel.Factory(
                        context = applicationContext,
                        sensorManager = sensorManager,
                        routeRepository = routeRepository
                    )
                )

                val historyViewModel: RouteHistoryViewModel = viewModel(
                    factory = RouteHistoryViewModel.Factory(routeRepository)
                )

                val mapDownloadViewModel: MapDownloadViewModel = viewModel(
                    factory = MapDownloadViewModel.Factory(applicationContext)
                )

                GarminDashApp(
                    cockpitViewModel = cockpitViewModel,
                    mapViewModel = mapViewModel,
                    historyViewModel = historyViewModel,
                    downloadViewModel = mapDownloadViewModel
                )
            }
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
}

enum class NavigationTab(val title: String) {
    DASHBOARD("Cockpit"),
    MAP("Mapa Offline"),
    HISTORY("Historial")
}

@Composable
fun GarminDashApp(
    cockpitViewModel: CockpitViewModel,
    mapViewModel: MapViewModel,
    historyViewModel: RouteHistoryViewModel,
    downloadViewModel: MapDownloadViewModel
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showMapDownloadManager by remember { mutableStateOf(false) }

    val telemetry by cockpitViewModel.telemetryState.collectAsState()
    val altitudeSource by cockpitViewModel.altitudeSource.collectAsState()
    val routesList by historyViewModel.routesList.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = com.example.ui.theme.CockpitSurfaceVariant,
                modifier = Modifier.testTag("navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.DASHBOARD,
                    onClick = { selectedTab = NavigationTab.DASHBOARD },
                    icon = { Icon(imageVector = Icons.Default.Dashboard, contentDescription = "Cockpit") },
                    label = { Text("Cockpit", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.SleekOrange,
                        selectedTextColor = com.example.ui.theme.SleekOrange,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = com.example.ui.theme.CockpitSurface
                    ),
                    modifier = Modifier.testTag("nav_tab_cockpit")
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.MAP,
                    onClick = { selectedTab = NavigationTab.MAP },
                    icon = { Icon(imageVector = Icons.Default.Map, contentDescription = "Mapa") },
                    label = { Text("Mapa", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.SleekOrange,
                        selectedTextColor = com.example.ui.theme.SleekOrange,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = com.example.ui.theme.CockpitSurface
                    ),
                    modifier = Modifier.testTag("nav_tab_map")
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.HISTORY,
                    onClick = { selectedTab = NavigationTab.HISTORY },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = "Historial") },
                    label = { Text("Historial", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.SleekOrange,
                        selectedTextColor = com.example.ui.theme.SleekOrange,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = com.example.ui.theme.CockpitSurface
                    ),
                    modifier = Modifier.testTag("nav_tab_history")
                )
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.DASHBOARD -> DashboardScreen(
                    telemetry = telemetry,
                    altitudeSource = altitudeSource
                )

                NavigationTab.MAP -> {
                    if (showMapDownloadManager) {
                        MapDownloadScreen(
                            downloadViewModel = downloadViewModel,
                            onBackClick = { showMapDownloadManager = false },
                            onSelectToLoad = { file ->
                                mapViewModel.selectMbtilesFile(file)
                                showMapDownloadManager = false
                            }
                        )
                    } else {
                        MapScreen(
                            latitude = telemetry.latitude,
                            longitude = telemetry.longitude,
                            headingDegrees = telemetry.headingDegrees,
                            gpsAccuracyMeters = telemetry.gpsAccuracyMeters,
                            compassAccuracy = telemetry.compassAccuracy,
                            isCalibrationNeeded = telemetry.isCalibrationNeeded,
                            mapViewModel = mapViewModel,
                            historyViewModel = historyViewModel,
                            onOpenDownloadManager = { showMapDownloadManager = true }
                        )
                    }
                }

                NavigationTab.HISTORY -> RouteHistoryScreen(
                    routes = routesList,
                    onDeleteRoute = { routeId -> historyViewModel.deleteRoute(routeId) },
                    onExportGpx = { routeId, onResult ->
                        historyViewModel.exportGpx(routeId) { file ->
                            onResult(file?.absolutePath ?: "Error exportando GPX")
                        }
                    },
                    onGetRouteDetail = { routeId -> historyViewModel.getRouteDetail(routeId) },
                    onLoadRouteOnMap = { routeId ->
                        mapViewModel.loadHistoricalRoute(routeId)
                        selectedTab = NavigationTab.MAP
                    }
                )
            }
        }
    }
}
