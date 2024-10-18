package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.database.MeshLogRepository
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
    val deviceMetrics: List<Telemetry> = emptyList(),
    val environmentMetrics: List<Telemetry> = emptyList(),
    val environmentDisplayFahrenheit: Boolean = false,
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()

    companion object {
        val Empty = MetricsState()
    }
}

@HiltViewModel
class MetricsViewModel @Inject constructor(
    meshLogRepository: MeshLogRepository,
    radioConfigRepository: RadioConfigRepository,
) : ViewModel() {
    private val destNum = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = destNum.flatMapLatest { destNum ->
        combine(
            meshLogRepository.getTelemetryFrom(destNum),
            radioConfigRepository.moduleConfigFlow,
        ) { telemetry, config ->
            MetricsState(
                deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                environmentMetrics = telemetry.filter {
                    it.hasEnvironmentMetrics() && it.environmentMetrics.relativeHumidity >= 0f
                },
                environmentDisplayFahrenheit = config.telemetry.environmentDisplayFahrenheit,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = WhileSubscribed(),
        initialValue = MetricsState.Empty,
    )

    /**
     * Used to set the Node for which the user will see charts for.
     */
    fun setSelectedNode(nodeNum: Int) {
        destNum.value = nodeNum
    }
}
