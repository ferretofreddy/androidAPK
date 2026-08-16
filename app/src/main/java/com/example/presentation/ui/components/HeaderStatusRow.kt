package com.example.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Outdoor contrast palette
private val CardBackground = Color(0xFF15181C)
private val AmberPrimary = Color(0xFFEF9F27)
private val TextWarmSecondary = Color(0xFFF7F1E3)

@Composable
fun HeaderStatusRow(
    gpsAccuracyMeters: Float,
    satellitesUsed: Int = 0,
    satellitesVisible: Int = 0,
    isGpsSearching: Boolean = true,
    modifier: Modifier = Modifier
) {
    val fixLabel = when {
        isGpsSearching || gpsAccuracyMeters <= 0f -> {
            if (satellitesVisible > 0) "BUSCANDO ($satellitesUsed/$satellitesVisible)" else "BUSCANDO GPS..."
        }
        satellitesUsed > 0 && satellitesVisible > 0 -> {
            "FIX 3D · $satellitesUsed/$satellitesVisible"
        }
        else -> "FIX 3D"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("header_status_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chip 1: Satellites & GNSS Status (~50% width)
        StatusChip(
            icon = Icons.Default.SatelliteAlt,
            label = fixLabel,
            modifier = Modifier.weight(1f)
        )

        // Chip 2: GPS Precision (~50% width)
        StatusChip(
            icon = Icons.Default.GpsFixed,
            label = if (gpsAccuracyMeters > 0f) String.format(Locale.US, "±%.1fm", gpsAccuracyMeters) else "±--",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CardBackground, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.padding(end = 4.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                color = TextWarmSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
