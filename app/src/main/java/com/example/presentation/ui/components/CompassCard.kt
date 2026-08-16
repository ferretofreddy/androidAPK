package com.example.presentation.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sensor.CompassAccuracy
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val CardBackground = Color(0xFF15181C)
private val AmberPrimary = Color(0xFFEF9F27)
private val TextWarmSecondary = Color(0xFFF7F1E3)
private val TextMutedLabel = Color(0xFF8E887D)
private val RingColor = Color(0xFF2E3340)
private val NeedleNorthColor = Color(0xFFEF4444)
private val WarningRed = Color(0xFFFF3B30)

@Composable
fun CompassCard(
    headingDegrees: Float,
    compassAccuracy: CompassAccuracy,
    isCalibrationNeeded: Boolean,
    modifier: Modifier = Modifier
) {
    val isAccuracyLow = isCalibrationNeeded || compassAccuracy == CompassAccuracy.LOW || compassAccuracy == CompassAccuracy.UNRELIABLE
    val statusDotColor = if (isAccuracyLow) WarningRed else AmberPrimary
    val statusText = when {
        isCalibrationNeeded -> "Mueve dispositivo en 8 (Baja precisión)"
        compassAccuracy == CompassAccuracy.UNRELIABLE -> "Calibración no confiable"
        compassAccuracy == CompassAccuracy.LOW -> "Precisión de brújula baja"
        compassAccuracy == CompassAccuracy.MEDIUM -> "Calibración media"
        else -> "Calibración óptima"
    }

    val normalizedHeading = (headingDegrees % 360 + 360) % 360
    val cardinal = getCardinalPoint(normalizedHeading)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("compass_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Calibration Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusDotColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = if (isAccuracyLow) WarningRed else TextMutedLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Middle: Circular Compass Canvas & Centered Text
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CompassCanvas(
                    headingDegrees = normalizedHeading,
                    modifier = Modifier.fillMaxSize()
                )

                // Center Degrees + Cardinal Point Text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format(Locale.US, "%03d°", normalizedHeading.toInt()),
                        color = AmberPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = cardinal,
                        color = TextWarmSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassCanvas(
    headingDegrees: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (minOf(size.width, size.height) / 2f) - 16.dp.toPx()
        if (radius <= 0) return@Canvas

        // Draw Outer Ring
        drawCircle(
            color = RingColor,
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw Fixed Top Pointer (North direction indicator triangle)
        val topTrianglePath = Path().apply {
            moveTo(center.x, center.y - radius - 8.dp.toPx())
            lineTo(center.x - 7.dp.toPx(), center.y - radius + 6.dp.toPx())
            lineTo(center.x + 7.dp.toPx(), center.y - radius + 6.dp.toPx())
            close()
        }
        drawPath(path = topTrianglePath, color = NeedleNorthColor)

        // Ticks and Cardinals rotation calculation
        // Heading rotates dial counter-clockwise relative to top pointer
        val textPaint = Paint().apply {
            color = AmberPrimary.toArgb()
            textSize = 12.sp.toPx()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val cardinalPaint = Paint().apply {
            color = TextWarmSecondary.toArgb()
            textSize = 14.sp.toPx()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Draw 360 ticks around circle
        for (i in 0 until 360 step 15) {
            val angleRad = Math.toRadians((i - headingDegrees - 90).toDouble())
            val isMajor = i % 90 == 0
            val isMedium = i % 30 == 0

            val tickLength = when {
                isMajor -> 14.dp.toPx()
                isMedium -> 10.dp.toPx()
                else -> 6.dp.toPx()
            }

            val strokeWidth = when {
                isMajor -> 3.dp.toPx()
                isMedium -> 2.dp.toPx()
                else -> 1.dp.toPx()
            }

            val startX = center.x + (radius - tickLength) * cos(angleRad).toFloat()
            val startY = center.y + (radius - tickLength) * sin(angleRad).toFloat()
            val endX = center.x + radius * cos(angleRad).toFloat()
            val endY = center.y + radius * sin(angleRad).toFloat()

            drawLine(
                color = if (i == 0) NeedleNorthColor else AmberPrimary.copy(alpha = if (isMajor) 1f else 0.6f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth
            )

            // Draw N, E, S, W labels
            if (isMajor) {
                val labelText = when (i) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> ""
                }
                val labelRadius = radius - tickLength - 12.dp.toPx()
                val labelX = center.x + labelRadius * cos(angleRad).toFloat()
                val labelY = center.y + labelRadius * sin(angleRad).toFloat() + (cardinalPaint.textSize / 3f)

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        labelText,
                        labelX,
                        labelY,
                        if (i == 0) textPaint.apply { color = NeedleNorthColor.toArgb() } else cardinalPaint
                    )
                }
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
