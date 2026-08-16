package com.example.domain.model

import com.example.data.local.RouteEntity
import com.example.data.local.TrackPointEntity

data class RouteWithPoints(
    val route: RouteEntity,
    val trackPoints: List<TrackPointEntity>
)
