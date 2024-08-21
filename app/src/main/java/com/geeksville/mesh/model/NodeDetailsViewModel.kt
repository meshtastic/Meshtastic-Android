package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.DeviceMetrics
import com.geeksville.mesh.database.MeshLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Contains the data necessary to build the Device Metrics Chart.
 */
data class DataEntry(val time: Long, val deviceMetrics: DeviceMetrics)

@HiltViewModel
class NodeDetailsViewModel @Inject constructor(
    val nodeDB: NodeDB,
    private val meshLogRepository: MeshLogRepository
) : ViewModel() {

    private val _dataEntry = MutableStateFlow<List<DataEntry>>(emptyList())
    val dataEntries: StateFlow<List<DataEntry>> = _dataEntry

    /**
     * Gets the short name of the node identified by `nodeNum`.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getNodeName(nodeNum: Int): String? {
        return nodeDB.nodeDBbyNum.mapLatest { it[nodeNum] }.first()?.user?.shortName
    }

    /**
     * Used to set the Node we will be viewing charts for.
     */
    fun setSelectedNode(nodeNum: Int) {
        viewModelScope.launch {
            meshLogRepository.getTelemetryFrom(nodeNum).collect {
                val tmp = mutableListOf<DataEntry>()
                for (telemetry in it) {
                    val time = telemetry.time * 1000.0.toLong()
                    if (telemetry.hasDeviceMetrics())
                        tmp.add(DataEntry(time = time, DeviceMetrics(telemetry.deviceMetrics)))
                }
                _dataEntry.value = tmp
            }

        }
    }
}