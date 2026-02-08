/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.data.repository.TracerouteSnapshotRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.TracerouteMapAvailability
import org.meshtastic.core.model.evaluateTracerouteMapAvailability
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.fallback_node_name
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.strings.view_on_map
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.util.toMessageRes
import org.meshtastic.core.ui.util.toPosition
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

private const val DEFAULT_ID_SUFFIX_LENGTH = 4

private fun MeshPacket.hasValidSignal(): Boolean = (rx_time ?: 0) > 0 &&
    ((rx_snr ?: 0f) != 0f && (rx_rssi ?: 0) != 0) &&
    ((hop_start ?: 0) > 0 && (hop_start ?: 0) - (hop_limit ?: 0) == 0)

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
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val nodeRequestActions: NodeRequestActions,
    private val alertManager: AlertManager,
) : ViewModel() {
    private var destNum: Int? =
        runCatching { savedStateHandle.toRoute<NodesRoutes.NodeDetailGraph>().destNum }.getOrNull()

    private var jobs: Job? = null

    private val tracerouteOverlayCache = MutableStateFlow<Map<Int, TracerouteOverlay>>(emptyMap())

    private fun MeshLog.hasValidTraceroute(): Boolean =
        with(fromRadio.packet) { this?.decoded != null && decoded?.want_response == true && from == 0 && to == destNum }

    private fun MeshLog.hasValidNeighborInfo(): Boolean =
        with(fromRadio.packet) { this?.decoded != null && decoded?.want_response == true && from == 0 && to == destNum }

    /**
     * Creates a fallback node for hidden clients or nodes not yet in the database. This prevents the detail screen from
     * freezing when viewing unknown nodes.
     */
    private suspend fun createFallbackNode(nodeNum: Int): Node {
        val userId = DataPacket.nodeNumToDefaultId(nodeNum)
        val safeUserId = userId.padStart(DEFAULT_ID_SUFFIX_LENGTH, '0').takeLast(DEFAULT_ID_SUFFIX_LENGTH)
        val longName = getString(Res.string.fallback_node_name) + "  $safeUserId"
        val defaultUser =
            User(id = userId, long_name = longName, short_name = safeUserId, hw_model = HardwareModel.UNSET)

        return Node(num = nodeNum, user = defaultUser)
    }

    fun getUser(nodeNum: Int) = nodeRepository.getUser(nodeNum)

    fun deleteLog(uuid: String) = viewModelScope.launch(dispatchers.io) { meshLogRepository.deleteLog(uuid) }

    fun getTracerouteOverlay(requestId: Int): TracerouteOverlay? {
        val cached = tracerouteOverlayCache.value[requestId]
        if (cached != null) return cached

        val overlay =
            serviceRepository.tracerouteResponse.value
                ?.takeIf { it.requestId == requestId }
                ?.let { response ->
                    TracerouteOverlay(
                        requestId = response.requestId,
                        forwardRoute = response.forwardRoute,
                        returnRoute = response.returnRoute,
                    )
                }
                ?.takeIf { it.hasRoutes }

        if (overlay != null) {
            tracerouteOverlayCache.update { it + (requestId to overlay) }
        }

        return overlay
    }

    fun tracerouteSnapshotPositions(logUuid: String) = tracerouteSnapshotRepository.getSnapshotPositions(logUuid)

    fun clearTracerouteResponse() = serviceRepository.clearTracerouteResponse()

    fun tracerouteMapAvailability(forwardRoute: List<Int>, returnRoute: List<Int>): TracerouteMapAvailability =
        evaluateTracerouteMapAvailability(
            forwardRoute = forwardRoute,
            returnRoute = returnRoute,
            positionedNodeNums = positionedNodeNums(),
        )

    fun tracerouteMapAvailability(overlay: TracerouteOverlay): TracerouteMapAvailability =
        tracerouteMapAvailability(overlay.forwardRoute, overlay.returnRoute)

    fun positionedNodeNums(): Set<Int> =
        nodeRepository.nodeDBbyNum.value.values.filter { it.validPosition != null }.map { it.num }.toSet()

    init {
        viewModelScope.launch {
            serviceRepository.tracerouteResponse.filterNotNull().collect { response ->
                val overlay =
                    TracerouteOverlay(
                        requestId = response.requestId,
                        forwardRoute = response.forwardRoute,
                        returnRoute = response.returnRoute,
                    )
                if (overlay.hasRoutes) {
                    tracerouteOverlayCache.update { it + (response.requestId to overlay) }
                }
            }
        }
    }

    fun clearPosition() = viewModelScope.launch(dispatchers.io) {
        destNum?.let { meshLogRepository.deleteLogs(it, PortNum.POSITION_APP.value) }
    }

    fun onServiceAction(action: ServiceAction) = viewModelScope.launch { serviceRepository.onServiceAction(action) }

    private val _state = MutableStateFlow(MetricsState.Empty)
    val state: StateFlow<MetricsState> = _state

    private val _environmentState = MutableStateFlow(EnvironmentMetricsState())
    val environmentState: StateFlow<EnvironmentMetricsState> = _environmentState

    val effects: SharedFlow<NodeRequestEffect> = nodeRequestActions.effects

    val lastTraceRouteTime: StateFlow<Long?> =
        nodeRequestActions.lastTracerouteTimes.map { it[destNum] }.stateInWhileSubscribed(null)

    val lastRequestNeighborsTime: StateFlow<Long?> =
        nodeRequestActions.lastRequestNeighborTimes.map { it[destNum] }.stateInWhileSubscribed(null)

    fun requestUserInfo() {
        destNum?.let { nodeRequestActions.requestUserInfo(viewModelScope, it, state.value.node?.user?.long_name ?: "") }
    }

    fun requestPosition() {
        destNum?.let { nodeRequestActions.requestPosition(viewModelScope, it, state.value.node?.user?.long_name ?: "") }
    }

    fun requestTelemetry(type: TelemetryType) {
        destNum?.let {
            nodeRequestActions.requestTelemetry(viewModelScope, it, state.value.node?.user?.long_name ?: "", type)
        }
    }

    fun requestTraceroute() {
        destNum?.let {
            nodeRequestActions.requestTraceroute(viewModelScope, it, state.value.node?.user?.long_name ?: "")
        }
    }

    fun requestNeighborInfo() {
        destNum?.let {
            nodeRequestActions.requestNeighborInfo(viewModelScope, it, state.value.node?.user?.long_name ?: "")
        }
    }

    fun showLogDetail(titleRes: StringResource, annotatedMessage: AnnotatedString) {
        alertManager.showAlert(
            titleRes = titleRes,
            composableMessage = { SelectionContainer { Text(text = annotatedMessage) } },
        )
    }

    fun showTracerouteDetail(
        annotatedMessage: AnnotatedString,
        requestId: Int,
        responseLogUuid: String,
        overlay: TracerouteOverlay?,
        onViewOnMap: (Int, String) -> Unit,
        onShowError: (StringResource) -> Unit,
    ) {
        viewModelScope.launch {
            val snapshotPositions = tracerouteSnapshotRepository.getSnapshotPositions(responseLogUuid).first()
            alertManager.showAlert(
                titleRes = Res.string.traceroute,
                composableMessage = { SelectionContainer { Text(text = annotatedMessage) } },
                confirmTextRes = Res.string.view_on_map,
                onConfirm = {
                    val positionedNodeNums =
                        if (snapshotPositions.isNotEmpty()) {
                            snapshotPositions.keys
                        } else {
                            positionedNodeNums()
                        }
                    val availability =
                        evaluateTracerouteMapAvailability(
                            forwardRoute = overlay?.forwardRoute.orEmpty(),
                            returnRoute = overlay?.returnRoute.orEmpty(),
                            positionedNodeNums = positionedNodeNums,
                        )
                    val errorRes = availability.toMessageRes()
                    if (errorRes != null) {
                        onShowError(errorRes)
                    } else {
                        onViewOnMap(requestId, responseLogUuid)
                    }
                },
                dismissTextRes = Res.string.okay,
            )
        }
    }

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
                        combine(nodeRepository.nodeDBbyNum, nodeRepository.myNodeInfo) { nodes, myInfo ->
                            nodes[currentDestNum] to (nodes.keys.firstOrNull() to myInfo)
                        }
                            .distinctUntilChanged()
                            .collect { (node, localData) ->
                                val (ourNodeNum, myInfo) = localData
                                // Create a fallback node if not found in database (for hidden clients, etc.)
                                val actualNode = node ?: createFallbackNode(currentDestNum)
                                val pioEnv = if (currentDestNum == ourNodeNum) myInfo?.pioEnv else null
                                val hwModel = actualNode.user.hw_model?.value ?: 0
                                val deviceHardware =
                                    deviceHardwareRepository.getDeviceHardwareByModel(hwModel, target = pioEnv)

                                _state.update { state ->
                                    state.copy(
                                        node = actualNode,
                                        isLocal = currentDestNum == ourNodeNum,
                                        deviceHardware = deviceHardware.getOrNull(),
                                        reportedTarget = pioEnv,
                                    )
                                }
                            }
                    }

                    launch {
                        radioConfigRepository.deviceProfileFlow.collect { profile ->
                            val moduleConfig = profile.module_config
                            val displayUnits = profile.config?.display?.units
                            _state.update { state ->
                                state.copy(
                                    isManaged = profile.config?.security?.is_managed ?: false,
                                    isFahrenheit =
                                    moduleConfig?.telemetry?.environment_display_fahrenheit == true ||
                                        (displayUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL),
                                    displayUnits = displayUnits ?: Config.DisplayConfig.DisplayUnits.METRIC,
                                )
                            }
                        }
                    }

                    launch {
                        meshLogRepository.getTelemetryFrom(currentDestNum).collect { telemetry ->
                            _state.update { state ->
                                state.copy(
                                    deviceMetrics = telemetry.filter { it.device_metrics != null },
                                    powerMetrics = telemetry.filter { it.power_metrics != null },
                                    hostMetrics = telemetry.filter { it.host_metrics != null },
                                )
                            }
                            _environmentState.update { state ->
                                state.copy(
                                    environmentMetrics =
                                    telemetry.filter {
                                        it.environment_metrics != null &&
                                            it.environment_metrics?.relative_humidity != null &&
                                            it.environment_metrics?.temperature != null &&
                                            it.environment_metrics?.temperature?.isNaN()?.not() == true
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
                            meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.TRACEROUTE_APP.value),
                            meshLogRepository.getLogsFrom(currentDestNum, PortNum.TRACEROUTE_APP.value),
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
                        combine(
                            meshLogRepository.getLogsFrom(nodeNum = 0, PortNum.NEIGHBORINFO_APP.value),
                            meshLogRepository.getLogsFrom(currentDestNum, PortNum.NEIGHBORINFO_APP.value),
                        ) { request, response ->
                            _state.update { state ->
                                state.copy(
                                    neighborInfoRequests = request.filter { it.hasValidNeighborInfo() },
                                    neighborInfoResults = response,
                                )
                            }
                        }
                            .collect {}
                    }

                    launch {
                        meshLogRepository.getMeshPacketsFrom(
                            currentDestNum,
                            PortNum.POSITION_APP.value,
                        ).collect { packets ->
                            val distinctPositions =
                                packets
                                    .mapNotNull { it.toPosition() }
                                    .asFlow()
                                    .distinctUntilChanged { old, new ->
                                        old.time == new.time ||
                                            (old.latitude_i == new.latitude_i && old.longitude_i == new.longitude_i)
                                    }
                                    .toList()
                            _state.update { state -> state.copy(positionLogs = distinctPositions) }
                        }
                    }

                    launch {
                        meshLogRepository.getLogsFrom(currentDestNum, PortNum.PAXCOUNTER_APP.value).collect { logs ->
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
                            .map { it?.firmware_edition }
                            .distinctUntilChanged()
                            .collect { firmwareEdition ->
                                _state.update { state -> state.copy(firmwareEdition = firmwareEdition) }
                            }
                    }

                    Logger.d { "MetricsViewModel created" }
                } else {
                    Logger.d { "MetricsViewModel: destNum is null, skipping metrics flows initialization." }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d { "MetricsViewModel cleared" }
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
                val rxDateTime = dateFormat.format((position.time ?: 0).toLong() * 1000L)
                val latitude = (position.latitude_i ?: 0) * 1e-7
                val longitude = (position.longitude_i ?: 0) * 1e-7
                val altitude = position.altitude
                val satsInView = position.sats_in_view
                val speed = position.ground_speed
                val heading = "%.2f".format((position.ground_track ?: 0) * 1e-5)

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
                Logger.e(ex) { "Can't write file error" }
            }
        }
}
