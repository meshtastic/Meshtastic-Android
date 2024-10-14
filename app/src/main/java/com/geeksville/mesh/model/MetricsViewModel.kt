package com.geeksville.mesh.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.enums.EnumEntries

enum class MetricsPage(
    @StringRes val titleResId: Int,
    @DrawableRes val drawableResId: Int,
) {
    DEVICE(R.string.device, R.drawable.baseline_charging_station_24),
    ENVIRONMENT(R.string.environment, R.drawable.baseline_thermostat_24),
}

data class MetricsState(
    val pages: EnumEntries<MetricsPage> = MetricsPage.entries,
    val isLoading: Boolean = false,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val environmentMetrics: List<Telemetry> = emptyList(),
    val environmentDisplayFahrenheit: Boolean = false,
) {
    companion object {
        val Empty = MetricsState()
    }
}

@HiltViewModel
class MetricsViewModel @Inject constructor(
    val nodeDB: NodeDB,
    private val meshLogRepository: MeshLogRepository,
    radioConfigRepository: RadioConfigRepository,
) : ViewModel() {

    private val isLoading = MutableStateFlow(false)
    private val _deviceMetrics = MutableStateFlow<List<Telemetry>>(emptyList())
    private val _environmentMetrics = MutableStateFlow<List<Telemetry>>(emptyList())

    val state = combine(
        isLoading,
        _deviceMetrics,
        _environmentMetrics,
        radioConfigRepository.deviceProfileFlow,
    ) { isLoading, device, environment, profile ->
        MetricsState(
            isLoading = isLoading,
            deviceMetrics = device,
            environmentMetrics = environment,
            environmentDisplayFahrenheit = profile.moduleConfig.telemetry.environmentDisplayFahrenheit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = WhileSubscribed(5_000),
        initialValue = MetricsState.Empty,
    )

    /**
     * Gets the short name of the node identified by `nodeNum`.
     */
    fun getNodeName(nodeNum: Int): String? = nodeDB.nodeDBbyNum.value[nodeNum]?.user?.shortName

    /**
     * Used to set the Node for which the user will see charts for.
     */
    fun setSelectedNode(nodeNum: Int) {
        viewModelScope.launch {
            isLoading.value = true
            meshLogRepository.getTelemetryFrom(nodeNum).collect {
                val deviceList = mutableListOf<Telemetry>()
                val environmentList = mutableListOf<Telemetry>()
                for (telemetry in it) {
                    if (telemetry.hasDeviceMetrics()) {
                        deviceList.add(telemetry)
                    }
                    /* Avoiding negative outliers */
                    if (telemetry.hasEnvironmentMetrics() && telemetry.environmentMetrics.relativeHumidity >= 0f) {
                        environmentList.add(telemetry)
                    }
                }
                _deviceMetrics.value = deviceList
                _environmentMetrics.value = environmentList
                isLoading.value = false
            }
        }
    }
}
