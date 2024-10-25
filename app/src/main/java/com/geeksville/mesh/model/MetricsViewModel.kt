package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MetricsState(
    val isManaged: Boolean = true,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val environmentMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val hasTracerouteLogs: Boolean = false,
    val environmentDisplayFahrenheit: Boolean = false,
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()
    fun hasSignalMetrics() = signalMetrics.isNotEmpty()

    companion object {
        val Empty = MetricsState()
    }
}

data class TracerouteLogState(
    val requests: List<MeshLog> = emptyList(),
    val results: List<MeshPacket> = emptyList(),
) {
    companion object {
        val Empty = TracerouteLogState()
    }
}

@HiltViewModel
class MetricsViewModel @Inject constructor(
    meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
) : ViewModel() {
    private val destNum = MutableStateFlow(0)

    private fun MeshPacket.hasValidSignal(): Boolean =
        rxTime > 0 && (rxSnr != 0f && rxRssi != 0) && (hopStart > 0 && hopStart - hopLimit == 0)

    private fun MeshLog.hasValidTraceroute(): Boolean = with(fromRadio.packet) {
        hasDecoded() && decoded.wantResponse && from == 0 && to == destNum.value
    }

    fun getUser(nodeNum: Int) = radioConfigRepository.getUser(nodeNum)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracerouteState = destNum.flatMapLatest { destNum ->
        combine(
            meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP_VALUE),
            meshLogRepository.getMeshPacketsFrom(destNum),
        ) { request, response ->
            val test = request.filter { it.hasValidTraceroute() }
            TracerouteLogState(
                requests = test,
                results = response,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = TracerouteLogState.Empty,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = destNum.flatMapLatest { destNum ->
        combine(
            meshLogRepository.getTelemetryFrom(destNum),
            meshLogRepository.getMeshPacketsFrom(destNum),
            meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP_VALUE),
            radioConfigRepository.deviceProfileFlow,
        ) { telemetry, meshPackets, traceroute, profile ->
            val moduleConfig = profile.moduleConfig
            MetricsState(
                isManaged = profile.config.security.isManaged,
                deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                environmentMetrics = telemetry.filter {
                    it.hasEnvironmentMetrics() && it.environmentMetrics.relativeHumidity >= 0f
                },
                signalMetrics = meshPackets.filter { it.hasValidSignal() },
                hasTracerouteLogs = traceroute.any { it.hasValidTraceroute() },
                environmentDisplayFahrenheit = moduleConfig.telemetry.environmentDisplayFahrenheit,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = MetricsState.Empty,
    )

    /**
     * Used to set the Node for which the user will see charts for.
     */
    fun setSelectedNode(nodeNum: Int) {
        destNum.value = nodeNum
    }
}
