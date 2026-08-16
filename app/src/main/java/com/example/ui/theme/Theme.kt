package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GarminDashColorScheme = darkColorScheme(
    primary = SleekOrange,
    onPrimary = Color.White,
    primaryContainer = CockpitSurfaceVariant,
    onPrimaryContainer = SleekOrange,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = CockpitSurfaceVariant,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonGreen,
    onTertiary = Color.Black,
    background = CockpitBackground,
    onBackground = TextPrimary,
    surface = CockpitSurface,
    onSurface = TextPrimary,
    surfaceVariant = CockpitSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = NeonRed,
    onError = Color.White
)

@Composable
fun GarminDashTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GarminDashColorScheme,
        typography = Typography,
        content = content
    )
}
