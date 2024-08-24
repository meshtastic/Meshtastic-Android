package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.database.MeshLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class NodeDetailsViewModel @Inject constructor(
    val nodeDB: NodeDB,
    private val meshLogRepository: MeshLogRepository
) : ViewModel() {

    private val _deviceMetrics = MutableStateFlow<List<Telemetry>>(emptyList())
    val deviceMetrics: StateFlow<List<Telemetry>> = _deviceMetrics

    private val _environmentMetrics = MutableStateFlow<List<Telemetry>>(emptyList())
    val environmentMetrics: StateFlow<List<Telemetry>> = _environmentMetrics

    /**
     * Gets the short name of the node identified by `nodeNum`.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getNodeName(nodeNum: Int): String? {
        return nodeDB.nodeDBbyNum.mapLatest { it[nodeNum] }.first()?.user?.shortName
    }

    /**
     * Used to set the Node for which the user will see charts for.
     */
    fun setSelectedNode(nodeNum: Int) {
        viewModelScope.launch {
            meshLogRepository.getTelemetryFrom(nodeNum).collect {
                val deviceList = mutableListOf<Telemetry>()
                val environmentList = mutableListOf<Telemetry>()
                for (telemetry in it) {
                    if (telemetry.hasDeviceMetrics())
                        deviceList.add(telemetry)
                    /* Avoiding negative outliers */
                    if (telemetry.hasEnvironmentMetrics() && telemetry.environmentMetrics.relativeHumidity >= 0f)
                        environmentList.add(telemetry)
                }
                _deviceMetrics.value = deviceList
                _environmentMetrics.value = environmentList
            }
        }
    }
}
