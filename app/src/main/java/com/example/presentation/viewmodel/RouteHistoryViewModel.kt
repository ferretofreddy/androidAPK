package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RouteEntity
import com.example.data.repository.RouteRepository
import com.example.domain.model.RouteWithPoints
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class RouteHistoryViewModel(
    private val routeRepository: RouteRepository
) : ViewModel() {

    val routesList: StateFlow<List<RouteEntity>> = routeRepository.allRoutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRoute(routeId: Long) {
        viewModelScope.launch {
            routeRepository.deleteRoute(routeId)
        }
    }

    fun exportGpx(routeId: Long, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val file = routeRepository.exportRouteToGpx(routeId)
            onResult(file)
        }
    }

    suspend fun getRouteDetail(routeId: Long): RouteWithPoints? {
        return routeRepository.getRouteWithPoints(routeId)
    }

    class Factory(private val routeRepository: RouteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RouteHistoryViewModel(routeRepository) as T
        }
    }
}
