package com.geeksville.mesh.service

import com.geeksville.mesh.IMeshService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository class for managing the [IMeshService] instance and connection state
 */
@Singleton
class ServiceRepository @Inject constructor() {
    var meshService: IMeshService? = null
        private set

    fun setMeshService(service: IMeshService?) {
        meshService = service
    }

    // Connection state to our radio device
    private val _connectionState = MutableStateFlow(MeshService.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MeshService.ConnectionState> get() = _connectionState

    fun setConnectionState(connectionState: MeshService.ConnectionState) {
        _connectionState.value = connectionState
    }

    private val _tracerouteResponse = MutableStateFlow<String?>(null)
    val tracerouteResponse: StateFlow<String?> get() = _tracerouteResponse

    fun setTracerouteResponse(value: String?) {
        _tracerouteResponse.value = value
    }

    fun clearTracerouteResponse() {
        setTracerouteResponse(null)
    }
}
