package com.geeksville.mesh.service

import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject
import javax.inject.Singleton

data class WantConfigState(
    var nodeCount: Int = 0,
    var channelCount: Int = 0,
    var configCount: Int = 0,
    var moduleCount: Int = 0,
)

/**
 * Repository class for managing the [IMeshService] instance and connection state
 */
@Singleton
class ServiceRepository @Inject constructor() : Logging {
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> get() = _errorMessage

    fun setErrorMessage(text: String) {
        errormsg(text)
        _errorMessage.value = text
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private val _wantConfigState = MutableStateFlow(WantConfigState())
    val wantConfigState: StateFlow<WantConfigState> = _wantConfigState

    fun setWantConfigState(update: (old: WantConfigState) -> WantConfigState): WantConfigState =
        _wantConfigState.updateAndGet(update)

    private val _meshPacketFlow = MutableSharedFlow<MeshPacket>()
    val meshPacketFlow: SharedFlow<MeshPacket> get() = _meshPacketFlow

    suspend fun emitMeshPacket(packet: MeshPacket) {
        _meshPacketFlow.emit(packet)
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
