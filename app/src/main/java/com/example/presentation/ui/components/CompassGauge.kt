package com.example.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sensor.CompassAccuracy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassGauge(
    headingDegrees: Float,
    compassAccuracy: CompassAccuracy = CompassAccuracy.HIGH,
    isCalibrationNeeded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedHeading by animateFloatAsState(
        targetValue = headingDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "compassAnimation"
    )

    val cardinalDir = remember(animatedHeading.toInt()) {
        when (((animatedHeading + 22.5f) % 360 / 45).toInt()) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            7 -> "NW"
            else -> "N"
        }
    }

    val topArrowPath = remember { Path() }
    val northNeedlePath = remember { Path() }
    val southNeedlePath = remember { Path() }

    val accuracyColor = when (compassAccuracy) {
        CompassAccuracy.HIGH -> NeonGreen
        CompassAccuracy.MEDIUM -> NeonCyan
        CompassAccuracy.LOW -> NeonOrange
        CompassAccuracy.UNRELIABLE -> NeonRed
    }

    val accuracyLabel = when (compassAccuracy) {
        CompassAccuracy.HIGH -> "ACC: ALTA"
        CompassAccuracy.MEDIUM -> "ACC: MEDIA"
        CompassAccuracy.LOW -> "ACC: BAJA"
        CompassAccuracy.UNRELIABLE -> "ACC: DUDOSA"
    }

    Box(
        modifier = modifier
            .testTag("compass_gauge")
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 12.dp.toPx()

            // Outer Ring - colored by compass calibration accuracy
            drawCircle(
                color = accuracyColor.copy(alpha = 0.8f),
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Inner Ring
            drawCircle(
                color = accuracyColor.copy(alpha = 0.12f),
                radius = radius - 16.dp.toPx(),
                center = center
            )

            // Top fixed heading pointer arrow (North Indicator)
            topArrowPath.reset()
            topArrowPath.moveTo(center.x, center.y - radius - 6.dp.toPx())
            topArrowPath.lineTo(center.x - 8.dp.toPx(), center.y - radius + 8.dp.toPx())
            topArrowPath.lineTo(center.x + 8.dp.toPx(), center.y - radius + 8.dp.toPx())
            topArrowPath.close()
            drawPath(path = topArrowPath, color = NeonRed)

            // Rotating Compass Rose
            rotate(-animatedHeading, pivot = center) {
                // Ticks for every 10 degrees
                for (angle in 0 until 360 step 10) {
                    val rad = Math.toRadians(angle.toDouble())
                    val isMajor = angle % 30 == 0
                    val tickLength = if (isMajor) 12.dp.toPx() else 6.dp.toPx()

                    val startX = center.x + (radius - tickLength) * sin(rad).toFloat()
                    val startY = center.y - (radius - tickLength) * cos(rad).toFloat()
                    val endX = center.x + radius * sin(rad).toFloat()
                    val endY = center.y - radius * cos(rad).toFloat()

                    drawLine(
                        color = if (angle == 0) NeonRed else if (isMajor) NeonCyan else TextMuted,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMajor) 3.dp.toPx() else 1.5f.dp.toPx()
                    )
                }

                // Needle (Red North, Blue South)
                northNeedlePath.reset()
                northNeedlePath.moveTo(center.x, center.y - radius + 20.dp.toPx())
                northNeedlePath.lineTo(center.x - 6.dp.toPx(), center.y)
                northNeedlePath.lineTo(center.x + 6.dp.toPx(), center.y)
                northNeedlePath.close()
                drawPath(path = northNeedlePath, color = NeonRed)

                southNeedlePath.reset()
                southNeedlePath.moveTo(center.x, center.y + radius - 20.dp.toPx())
                southNeedlePath.lineTo(center.x - 6.dp.toPx(), center.y)
                southNeedlePath.lineTo(center.x + 6.dp.toPx(), center.y)
                southNeedlePath.close()
                drawPath(path = southNeedlePath, color = NeonCyan)
            }

            // Central Pivot
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BRÚJULA",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                text = "${animatedHeading.toInt()}°",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
            Text(
                text = cardinalDir,
                style = MaterialTheme.typography.labelMedium,
                color = NeonOrange,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = accuracyLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accuracyColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
