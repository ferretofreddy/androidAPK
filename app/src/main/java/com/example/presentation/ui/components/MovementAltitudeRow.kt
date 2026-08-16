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
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Height
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
import com.example.domain.model.AltitudeSource
import java.util.Locale

private val CardBackground = Color(0xFF15181C)
private val BadgeBackground = Color(0xFF262C38)
private val AmberPrimary = Color(0xFFEF9F27)
private val TextWarmSecondary = Color(0xFFF7F1E3)
private val TextMutedLabel = Color(0xFF8E887D)

@Composable
fun MovementAltitudeRow(
    speedKmh: Float,
    altitudeMeters: Double,
    altitudeSource: AltitudeSource,
    modifier: Modifier = Modifier
) {
    val isMoving = speedKmh >= 1.0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("movement_altitude_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Estado de Movimiento Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("movement_card")
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
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "MOVIMIENTO",
                        color = TextMutedLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isMoving) "En movimiento" else "Detenido",
                    color = AmberPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isMoving) "Acelerómetros activos" else "En reposo",
                    color = TextWarmSecondary,
                    fontSize = 10.sp
                )
            }
        }

        // Altitud Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("altitude_card")
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
                        imageVector = Icons.Default.Height,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ALTITUD",
                        color = TextMutedLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%.1f m", altitudeMeters),
                    color = AmberPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Source Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = BadgeBackground),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = altitudeSource.sourceName,
                        color = TextWarmSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
