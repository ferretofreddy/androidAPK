package com.example.domain.model

/**
 * Recording state representation for map route recording.
 */
sealed interface RouteRecordingState {
    object Idle : RouteRecordingState
    object Recording : RouteRecordingState
    object Paused : RouteRecordingState
    data class Saved(val routeId: Long) : RouteRecordingState
}
