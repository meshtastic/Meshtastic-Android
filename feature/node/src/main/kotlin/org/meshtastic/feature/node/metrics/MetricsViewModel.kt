/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.feature.node.metrics

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.fallback_node_name
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.Portnums.PortNum
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

private const val DEFAULT_ID_SUFFIX_LENGTH = 4

private fun MeshPacket.hasValidSignal(): Boolean =
    rxTime > 0 && (rxSnr != 0f && rxRssi != 0) && (hopStart > 0 && hopStart - hopLimit == 0)

@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class MetricsViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    private val dispatchers: CoroutineDispatchers,
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
) : ViewModel() {
    private var destNum: Int? =
        runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetailGraph>().destNum }.getOrNull()

    private var jobs: Job? = null

    private fun MeshLog.hasValidTraceroute(): Boolean =
        with(fromRadio.packet) { hasDecoded() && decoded.wantResponse && from == 0 && to == destNum }

    /**
     * Creates a fallback node for hidden clients or nodes not yet in the database. This prevents the detail screen from
     * freezing when viewing unknown nodes.
     */
    private suspend fun createFallbackNode(nodeNum: Int): Node {
        val userId = DataPacket.nodeNumToDefaultId(nodeNum)
        val safeUserId = userId.padStart(DEFAULT_ID_SUFFIX_LENGTH, '0').takeLast(DEFAULT_ID_SUFFIX_LENGTH)
        val longName = getString(Res.string.fallback_node_name) + "  $safeUserId"
        val defaultUser =
            MeshProtos.User.newBuilder()
                .setId(userId)
                .setLongName(longName)
                .setShortName(safeUserId)
                .setHwModel(MeshProtos.HardwareModel.UNSET)
                .build()

        return Node(num = nodeNum, user = defaultUser)
    }

    fun getUser(nodeNum: Int) = nodeRepository.getUser(nodeNum)

    fun deleteLog(uuid: String) = viewModelScope.launch(dispatchers.io) { meshLogRepository.deleteLog(uuid) }

    fun clearPosition() = viewModelScope.launch(dispatchers.io) {
        destNum?.let { meshLogRepository.deleteLogs(it, PortNum.POSITION_APP_VALUE) }
    }

    fun onServiceAction(action: ServiceAction) = viewModelScope.launch { serviceRepository.onServiceAction(action) }

    private val _state = MutableStateFlow(MetricsState.Empty)
    val state: StateFlow<MetricsState> = _state

    private val _environmentState = MutableStateFlow(EnvironmentMetricsState())
    val environmentState: StateFlow<EnvironmentMetricsState> = _environmentState

    private val _timeFrame = MutableStateFlow(TimeFrame.TWENTY_FOUR_HOURS)
    val timeFrame: StateFlow<TimeFrame> = _timeFrame

    init {
        initializeFlows()
    }

    fun setNodeId(id: Int) {
        if (destNum != id) {
            destNum = id
            initializeFlows()
        }
    }

    @Suppress("LongMethod")
    private fun initializeFlows() {
        jobs?.cancel()
        val currentDestNum = destNum
        jobs =
            viewModelScope.launch {
                if (currentDestNum != null) {
                    launch {
                        nodeRepository.nodeDBbyNum
                            .mapLatest { nodes -> nodes[currentDestNum] to nodes.keys.firstOrNull() }
                            .distinctUntilChanged()
                            .collect { (node, ourNode) ->
                                // Create a fallback node if not found in database (for hidden clients, etc.)
                                val actualNode = node ?: createFallbackNode(currentDestNum)
                                val deviceHardware =
                                    actualNode.user.hwModel.safeNumber().let {
                                        deviceHardwareRepository.getDeviceHardwareByModel(it)
                                    }
                                _state.update { state ->
                                    state.copy(
                                        node = actualNode,
                                        isLocal = currentDestNum == ourNode,
                                        deviceHardware = deviceHardware.getOrNull(),
                                    )
                                }
                            }
                    }

                    launch {
                        radioConfigRepository.deviceProfileFlow.collect { profile ->
                            val moduleConfig = profile.moduleConfig
                            _state.update { state ->
                                state.copy(
                                    isManaged = profile.config.security.isManaged,
                                    isFahrenheit = moduleConfig.telemetry.environmentDisplayFahrenheit,
                                    displayUnits = profile.config.display.units,
                                )
                            }
                        }
                    }

                    launch {
                        meshLogRepository.getTelemetryFrom(currentDestNum).collect { telemetry ->
                            _state.update { state ->
                                state.copy(
                                    deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                                    powerMetrics = telemetry.filter { it.hasPowerMetrics() },
                                    hostMetrics = telemetry.filter { it.hasHostMetrics() },
                                )
                            }
                            _environmentState.update { state ->
                                state.copy(
                                    environmentMetrics =
                                    telemetry.filter {
                                        it.hasEnvironmentMetrics() &&
                                            it.environmentMetrics.hasRelativeHumidity() &&
                                            it.environmentMetrics.hasTemperature() &&
                                            !it.environmentMetrics.temperature.isNaN()
                                    },
                                )
                            }
                        }
                    }

                    launch {
                        meshLogRepository.getMeshPacketsFrom(currentDestNum).collect { meshPackets ->
                            _state.update { state ->
                                state.copy(signalMetrics = meshPackets.filter { it.hasValidSignal() })
                            }
                        }
                    }

                    launch {
                        combine(
                            meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP_VALUE),
                            meshLogRepository.getLogsFrom(currentDestNum, PortNum.TRACEROUTE_APP_VALUE),
                        ) { request, response ->
                            _state.update { state ->
                                state.copy(
                                    tracerouteRequests = request.filter { it.hasValidTraceroute() },
                                    tracerouteResults = response,
                                )
                            }
                        }
                            .collect {}
                    }

                    launch {
                        meshLogRepository.getMeshPacketsFrom(
                            currentDestNum,
                            PortNum.POSITION_APP_VALUE,
                        ).collect { packets ->
                            val distinctPositions =
                                packets
                                    .mapNotNull { it.toPosition() }
                                    .asFlow()
                                    .distinctUntilChanged { old, new ->
                                        old.time == new.time ||
                                            (old.latitudeI == new.latitudeI && old.longitudeI == new.longitudeI)
                                    }
                                    .toList()
                            _state.update { state -> state.copy(positionLogs = distinctPositions) }
                        }
                    }

                    launch {
                        meshLogRepository.getLogsFrom(
                            currentDestNum,
                            Portnums.PortNum.PAXCOUNTER_APP_VALUE,
                        ).collect { logs ->
                            _state.update { state -> state.copy(paxMetrics = logs) }
                        }
                    }

                    launch {
                        firmwareReleaseRepository.stableRelease.filterNotNull().collect { latestStable ->
                            _state.update { state -> state.copy(latestStableFirmware = latestStable) }
                        }
                    }

                    launch {
                        firmwareReleaseRepository.alphaRelease.filterNotNull().collect { latestAlpha ->
                            _state.update { state -> state.copy(latestAlphaFirmware = latestAlpha) }
                        }
                    }

                    launch {
                        meshLogRepository
                            .getMyNodeInfo()
                            .map { it?.firmwareEdition }
                            .distinctUntilChanged()
                            .collect { firmwareEdition ->
                                _state.update { state -> state.copy(firmwareEdition = firmwareEdition) }
                            }
                    }

                    Timber.d("MetricsViewModel created")
                } else {
                    Timber.d("MetricsViewModel: destNum is null, skipping metrics flows initialization.")
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("MetricsViewModel cleared")
    }

    fun setTimeFrame(timeFrame: TimeFrame) {
        _timeFrame.value = timeFrame
    }

    /** Write the persisted Position data out to a CSV file in the specified location. */
    fun savePositionCSV(uri: Uri) = viewModelScope.launch(dispatchers.main) {
        val positions = state.value.positionLogs
        writeToUri(uri) { writer ->
            writer.appendLine(
                "\"date\",\"time\",\"latitude\",\"longitude\",\"altitude\",\"satsInView\",\"speed\",\"heading\"",
            )

            val dateFormat = SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())

            positions.forEach { position ->
                val rxDateTime = dateFormat.format(position.time * 1000L)
                val latitude = position.latitudeI * 1e-7
                val longitude = position.longitudeI * 1e-7
                val altitude = position.altitude
                val satsInView = position.satsInView
                val speed = position.groundSpeed
                val heading = "%.2f".format(position.groundTrack * 1e-5)

                // date,time,latitude,longitude,altitude,satsInView,speed,heading
                writer.appendLine(
                    "$rxDateTime,\"$latitude\",\"$longitude\",\"$altitude\",\"$satsInView\",\"$speed\",\"$heading\"",
                )
            }
        }
    }

    private suspend inline fun writeToUri(uri: Uri, crossinline block: suspend (BufferedWriter) -> Unit) =
        withContext(dispatchers.io) {
            try {
                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                        BufferedWriter(fileWriter).use { writer -> block.invoke(writer) }
                    }
                }
            } catch (ex: FileNotFoundException) {
                Timber.e(ex, "Can't write file error")
            }
        }
}
