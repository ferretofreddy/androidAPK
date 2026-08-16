package com.example.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CockpitSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun AttitudeGauge(
    pitchDegrees: Float,
    rollDegrees: Float,
    modifier: Modifier = Modifier
) {
    val animatedPitch by animateFloatAsState(
        targetValue = pitchDegrees.coerceIn(-60f, 60f),
        animationSpec = tween(300),
        label = "pitchAnimation"
    )
    val animatedRoll by animateFloatAsState(
        targetValue = rollDegrees.coerceIn(-90f, 90f),
        animationSpec = tween(300),
        label = "rollAnimation"
    )

    val clipPath = remember { Path() }

    Box(
        modifier = modifier
            .testTag("attitude_gauge")
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
            val radius = size.minDimension / 2f - 8.dp.toPx()

            // Clip mask to circular instrument
            clipPath.reset()
            clipPath.addOval(androidx.compose.ui.geometry.Rect(center, radius))

            drawContext.canvas.save()
            drawContext.canvas.clipPath(clipPath)

            // Sky & Ground representation rotated by roll angle
            rotate(-animatedRoll, pivot = center) {
                val pitchPixelOffset = (animatedPitch / 60f) * (radius * 0.8f)
                val horizonY = center.y + pitchPixelOffset

                // Sky (Blue)
                drawRect(
                    color = Color(0xFF1E3A8A),
                    topLeft = Offset(center.x - radius * 2, center.y - radius * 2),
                    size = Size(radius * 4, (horizonY - (center.y - radius * 2)).coerceAtLeast(0f))
                )

                // Ground (Brown/Dark)
                drawRect(
                    color = Color(0xFF451A03),
                    topLeft = Offset(center.x - radius * 2, horizonY),
                    size = Size(radius * 4, radius * 4)
                )

                // Horizon Line
                drawLine(
                    color = NeonCyan,
                    start = Offset(center.x - radius * 1.5f, horizonY),
                    end = Offset(center.x + radius * 1.5f, horizonY),
                    strokeWidth = 3.dp.toPx()
                )

                // Pitch Ladder Lines (+10, +20, -10, -20)
                for (pitchStep in listOf(-30, -20, -10, 10, 20, 30)) {
                    val stepOffset = -(pitchStep / 60f) * (radius * 0.8f)
                    val lineY = horizonY + stepOffset

                    if (lineY >= center.y - radius && lineY <= center.y + radius) {
                        val lineWidth = if (pitchStep % 20 == 0) 40.dp.toPx() else 24.dp.toPx()
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(center.x - lineWidth / 2, lineY),
                            end = Offset(center.x + lineWidth / 2, lineY),
                            strokeWidth = 1.5f.dp.toPx()
                        )
                    }
                }
            }

            drawContext.canvas.restore()

            // Fixed Aircraft Wings Symbol in foreground
            drawLine(
                color = NeonOrange,
                start = Offset(center.x - 36.dp.toPx(), center.y),
                end = Offset(center.x - 12.dp.toPx(), center.y),
                strokeWidth = 4.dp.toPx()
            )
            drawLine(
                color = NeonOrange,
                start = Offset(center.x + 12.dp.toPx(), center.y),
                end = Offset(center.x + 36.dp.toPx(), center.y),
                strokeWidth = 4.dp.toPx()
            )
            drawCircle(
                color = NeonOrange,
                radius = 4.dp.toPx(),
                center = center
            )

            // Outer Instrument Bezel
            drawCircle(
                color = CockpitSurfaceVariant,
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = "INCLINACIÓN (PITCH / ROLL)",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Row {
                Text(
                    text = "P: ${animatedPitch.toInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "R: ${animatedRoll.toInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonOrange,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
