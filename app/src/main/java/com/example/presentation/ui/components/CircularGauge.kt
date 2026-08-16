package com.example.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    title: String,
    unit: String,
    modifier: Modifier = Modifier,
    gaugeColor: Color = NeonCyan,
    showWarningZone: Boolean = false
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minValue, maxValue),
        animationSpec = tween(durationMillis = 600),
        label = "gaugeAnimation"
    )

    val fraction = ((animatedValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val startAngle = 135f
    val sweepAngle = 270f
    val currentAngle = startAngle + (sweepAngle * fraction)

    Box(
        modifier = modifier
            .testTag("circular_gauge_${title.lowercase().replace(" ", "_")}")
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
            val strokeWidth = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f - 8.dp.toPx()

            val arcSize = Size(radius * 2f, radius * 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)

            // Background Track
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Warning Zone if applicable (e.g. high speed)
            if (showWarningZone) {
                drawArc(
                    color = NeonRed.copy(alpha = 0.35f),
                    startAngle = startAngle + (sweepAngle * 0.8f),
                    sweepAngle = sweepAngle * 0.2f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Filled Arc
            val activeColor = when {
                showWarningZone && fraction > 0.85f -> NeonRed
                showWarningZone && fraction > 0.70f -> NeonOrange
                else -> gaugeColor
            }

            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fraction,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Tick Marks
            val numTicks = 9
            for (i in 0 until numTicks) {
                val tickFraction = i.toFloat() / (numTicks - 1)
                val tickAngle = Math.toRadians((startAngle + sweepAngle * tickFraction).toDouble())
                val innerR = radius - strokeWidth / 2f - 4.dp.toPx()
                val outerR = radius - strokeWidth / 2f - 12.dp.toPx()

                val startX = center.x + innerR * cos(tickAngle).toFloat()
                val startY = center.y + innerR * sin(tickAngle).toFloat()
                val endX = center.x + outerR * cos(tickAngle).toFloat()
                val endY = center.y + outerR * sin(tickAngle).toFloat()

                drawLine(
                    color = if (tickFraction <= fraction) activeColor.copy(alpha = 0.8f) else TextMuted.copy(alpha = 0.4f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Needle Dot
            val needleRad = Math.toRadians(currentAngle.toDouble())
            val needleR = radius - strokeWidth / 2f
            val dotX = center.x + needleR * cos(needleRad).toFloat()
            val dotY = center.y + needleR * sin(needleRad).toFloat()

            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = if (value % 1 == 0f) value.toInt().toString() else String.format("%.1f", value),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = gaugeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
