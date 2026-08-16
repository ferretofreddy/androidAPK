package com.example.domain.model

/**
 * Abstract source for altitude measurements in GarminDash.
 * Transparently indicates to the UI whether altitude is coming from
 * a hardware Barometer, smoothed GPS elevation, or Fused (GNSS + Barometer).
 *
 * DIAGNÓSTICO DE ALTITUD ESTANCADA RESUELTO:
 * El estancamiento de altitud se debía a la variancia del filtro Kalman (processNoiseQ = 1e-4)
 * en LocationFusionManager que hacía colapsar la ganancia k. Corregido ajustando processNoiseQ = 0.5
 * y permitiendo recalibraciones continuas de deriva barométrica vs GPS en tiempo real.
 */
sealed interface AltitudeSource {
    val altitudeMeters: Double
    val sourceName: String

    data class Fused(
        override val altitudeMeters: Double,
        val pressureHpa: Float,
        val isGpsCalibrated: Boolean,
        val gpsAccuracyMeters: Float
    ) : AltitudeSource {
        override val sourceName: String = "Fusión GNSS + Barómetro"
    }

    data class Barometric(
        override val altitudeMeters: Double,
        val pressureHpa: Float
    ) : AltitudeSource {
        override val sourceName: String = "Barómetro (Hardware)"
    }

    data class GpsDerived(
        override val altitudeMeters: Double,
        val accuracyMeters: Float
    ) : AltitudeSource {
        override val sourceName: String = "GPS (Suavizado)"
    }
}

