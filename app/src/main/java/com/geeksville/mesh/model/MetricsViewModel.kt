package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetricsState(
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val environmentMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshPacket> = emptyList(),
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()
    fun hasSignalMetrics() = signalMetrics.isNotEmpty()
    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()

    companion object {
        val Empty = MetricsState()
    }
}

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val dispatchers: CoroutineDispatchers,
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
) : ViewModel(), Logging {
    private val destNum = MutableStateFlow(0)

    private fun MeshPacket.hasValidSignal(): Boolean =
        rxTime > 0 && (rxSnr != 0f && rxRssi != 0) && (hopStart > 0 && hopStart - hopLimit == 0)

    private fun MeshLog.hasValidTraceroute(): Boolean = with(fromRadio.packet) {
        hasDecoded() && decoded.wantResponse && from == 0 && to == destNum.value
    }

    fun getUser(nodeNum: Int) = radioConfigRepository.getUser(nodeNum)

    fun deleteLog(uuid: String) = viewModelScope.launch(dispatchers.io) {
        meshLogRepository.deleteLog(uuid)
    }

    private val _state = MutableStateFlow(MetricsState.Empty)
    val state: StateFlow<MetricsState> = _state

    init {
        radioConfigRepository.deviceProfileFlow.onEach { profile ->
            val moduleConfig = profile.moduleConfig
            _state.update { state ->
                state.copy(
                    isManaged = profile.config.security.isManaged,
                    isFahrenheit = moduleConfig.telemetry.environmentDisplayFahrenheit,
                )
            }
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        destNum.flatMapLatest { destNum ->
            meshLogRepository.getTelemetryFrom(destNum).onEach { telemetry ->
                _state.update { state ->
                    state.copy(
                        deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                        environmentMetrics = telemetry.filter {
                            it.hasEnvironmentMetrics() && it.environmentMetrics.relativeHumidity >= 0f
                        },
                    )
                }
            }
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        destNum.flatMapLatest { destNum ->
            meshLogRepository.getMeshPacketsFrom(destNum).onEach { meshPackets ->
                _state.update { state ->
                    state.copy(signalMetrics = meshPackets.filter { it.hasValidSignal() })
                }
            }
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        destNum.flatMapLatest { destNum ->
            combine(
                meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP_VALUE),
                meshLogRepository.getMeshPacketsFrom(destNum, PortNum.TRACEROUTE_APP_VALUE),
            ) { request, response ->
                _state.update { state ->
                    state.copy(
                        tracerouteRequests = request.filter { it.hasValidTraceroute() },
                        tracerouteResults = response,
                    )
                }
            }
        }.launchIn(viewModelScope)

        debug("MetricsViewModel created")
    }

    override fun onCleared() {
        super.onCleared()
        debug("MetricsViewModel cleared")
    }

    /**
     * Used to set the Node for which the user will see charts for.
     */
    fun setSelectedNode(nodeNum: Int) {
        destNum.value = nodeNum
    }
}
