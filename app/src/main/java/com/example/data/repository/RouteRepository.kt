package com.example.data.repository

import android.content.Context
import com.example.data.local.RouteDao
import com.example.data.local.RouteEntity
import com.example.data.local.TrackPointEntity
import com.example.domain.model.RouteWithPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RouteRepository(
    private val context: Context,
    private val routeDao: RouteDao
) {

    val allRoutes: Flow<List<RouteEntity>> = routeDao.getAllRoutes()

    suspend fun saveRecordedRoute(
        routeName: String,
        startTime: Long,
        endTime: Long,
        trackPoints: List<TrackPointEntity>,
        totalDistanceMeters: Double,
        elevationGainMeters: Double
    ): Long = withContext(Dispatchers.IO) {
        if (trackPoints.isEmpty()) return@withContext 0L

        val durationSeconds = (endTime - startTime) / 1000L
        val distanceKm = totalDistanceMeters / 1000.0
        val durationHours = durationSeconds / 3600.0

        val avgSpeedKmh = if (durationHours > 0) distanceKm / durationHours else 0.0
        val maxSpeedKmh = trackPoints.maxOfOrNull { it.speedKmh.toDouble() } ?: 0.0

        val routeEntity = RouteEntity(
            name = routeName.ifBlank { "Ruta GarminDash ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(startTime))}" },
            startTimeTimestamp = startTime,
            endTimeTimestamp = endTime,
            totalDistanceMeters = totalDistanceMeters,
            durationSeconds = durationSeconds,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            elevationGainMeters = elevationGainMeters,
            pointCount = trackPoints.size
        )

        val routeId = routeDao.insertRoute(routeEntity)
        val pointsWithRouteId = trackPoints.map { it.copy(routeId = routeId) }
        routeDao.insertTrackPoints(pointsWithRouteId)

        routeId
    }

    suspend fun getRouteWithPoints(routeId: Long): RouteWithPoints? = withContext(Dispatchers.IO) {
        val route = routeDao.getRouteById(routeId) ?: return@withContext null
        val points = routeDao.getTrackPointsForRoute(routeId)
        RouteWithPoints(route, points)
    }

    suspend fun deleteRoute(routeId: Long) = withContext(Dispatchers.IO) {
        routeDao.deleteRouteWithPoints(routeId)
    }

    suspend fun exportRouteToGpx(routeId: Long): File? = withContext(Dispatchers.IO) {
        val routeWithPoints = getRouteWithPoints(routeId) ?: return@withContext null
        val route = routeWithPoints.route
        val points = routeWithPoints.trackPoints

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val gpxDir = File(context.getExternalFilesDir(null), "GarminDash_GPX")
        if (!gpxDir.exists()) {
            gpxDir.mkdirs()
        }

        val sanitizeName = route.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(gpxDir, "${sanitizeName}_${route.id}.gpx")

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GarminDash\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>").append(escapeXml(route.name)).append("</name>\n")
        sb.append("    <time>").append(isoFormat.format(Date(route.startTimeTimestamp))).append("</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(route.name)).append("</name>\n")
        sb.append("    <trkseg>\n")

        for (pt in points) {
            sb.append("      <trkpt lat=\"").append(pt.latitude).append("\" lon=\"").append(pt.longitude).append("\">\n")
            sb.append("        <ele>").append(pt.altitudeMeters).append("</ele>\n")
            sb.append("        <time>").append(isoFormat.format(Date(pt.timestamp))).append("</time>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        FileWriter(file).use { writer ->
            writer.write(sb.toString())
        }

        file
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
