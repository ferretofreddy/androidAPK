package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AltitudeSource
import com.example.domain.model.CockpitTelemetry
import com.example.presentation.ui.components.BearingSpeedRow
import com.example.presentation.ui.components.CompassCard
import com.example.presentation.ui.components.HeaderStatusRow
import com.example.presentation.ui.components.LatLngCard
import com.example.presentation.ui.components.MovementAltitudeRow

private val DarkBackground = Color(0xFF0B0D10)

/**
 * Cockpit Dashboard Screen — ROADMAP 2 (5-Row Fixed Layout, No Scroll)
 * Outdoor-optimized high contrast layout using weight-based sizing for 100% screen utilization.
 */
@Composable
fun DashboardScreen(
    telemetry: CockpitTelemetry,
    altitudeSource: AltitudeSource
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("dashboard_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fila 1 (~7% height): Fix, Precision, Battery Chips
            HeaderStatusRow(
                gpsAccuracyMeters = telemetry.gpsAccuracyMeters,
                satellitesUsed = telemetry.satellitesUsed,
                satellitesVisible = telemetry.satellitesVisible,
                isGpsSearching = telemetry.isGpsSearching,
                modifier = Modifier.weight(0.07f)
            )

            // Fila 2 (~11% height): Latitud | Longitud Card
            LatLngCard(
                latitude = telemetry.latitude,
                longitude = telemetry.longitude,
                modifier = Modifier.weight(0.11f)
            )

            // Fila 3 (~38% height): Circular Rotating Compass & Calibration Indicator (Dominant Row)
            CompassCard(
                headingDegrees = telemetry.headingDegrees,
                compassAccuracy = telemetry.compassAccuracy,
                isCalibrationNeeded = telemetry.isCalibrationNeeded,
                modifier = Modifier.weight(0.38f)
            )

            // Fila 4 (~22% height): Orientación (Bearing) | Velocidad (km/h)
            BearingSpeedRow(
                headingDegrees = telemetry.headingDegrees,
                speedKmh = telemetry.speedKmh,
                modifier = Modifier.weight(0.22f)
            )

            // Fila 5 (~22% height): Estado Movimiento | Altitud Fusionada (m)
            MovementAltitudeRow(
                speedKmh = telemetry.speedKmh,
                altitudeMeters = telemetry.altitudeMeters,
                altitudeSource = altitudeSource,
                modifier = Modifier.weight(0.22f)
            )
        }
    }
}
