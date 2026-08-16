package com.example.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val CardBackground = Color(0xFF15181C)
private val AmberPrimary = Color(0xFFEF9F27)
private val TextWarmSecondary = Color(0xFFF7F1E3)
private val TextMutedLabel = Color(0xFF8E887D)

@Composable
fun BearingSpeedRow(
    headingDegrees: Float,
    speedKmh: Float,
    modifier: Modifier = Modifier
) {
    val normalizedHeading = (headingDegrees % 360 + 360) % 360
    val cardinal = getCardinalPoint(normalizedHeading)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bearing_speed_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Orientación (Bearing Card)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("bearing_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ORIENTACIÓN",
                        color = TextMutedLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%03d° %s", normalizedHeading.toInt(), cardinal),
                    color = AmberPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Rumbo numérico",
                    color = TextWarmSecondary,
                    fontSize = 10.sp
                )
            }
        }

        // Velocidad (Speed Card)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("speed_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "VELOCIDAD",
                        color = TextMutedLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", speedKmh),
                        color = AmberPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "km/h",
                        color = TextWarmSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "GPS Fusión",
                    color = TextWarmSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun getCardinalPoint(degrees: Float): String {
    val norm = (degrees % 360 + 360) % 360
    return when {
        norm >= 337.5 || norm < 22.5 -> "N"
        norm >= 22.5 && norm < 67.5 -> "NE"
        norm >= 67.5 && norm < 112.5 -> "E"
        norm >= 112.5 && norm < 157.5 -> "SE"
        norm >= 157.5 && norm < 202.5 -> "S"
        norm >= 202.5 && norm < 247.5 -> "SW"
        norm >= 247.5 && norm < 292.5 -> "W"
        else -> "NW"
    }
}
