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

package com.geeksville.mesh.model

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.Position
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.FirmwareRelease
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.navigation.NodesRoutes
import com.geeksville.mesh.repository.api.DeviceHardwareRepository
import com.geeksville.mesh.repository.api.FirmwareReleaseRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.ui.map.MAP_STYLE_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DEFAULT_ID_SUFFIX_LENGTH = 4

data class MetricsState(
    val isLocal: Boolean = false,
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val displayUnits: DisplayUnits = DisplayUnits.METRIC,
    val node: Node? = null,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val powerMetrics: List<Telemetry> = emptyList(),
    val hostMetrics: List<Telemetry> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshPacket> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
    val isLocalDevice: Boolean = false,
    val latestStableFirmware: FirmwareRelease = FirmwareRelease(),
    val latestAlphaFirmware: FirmwareRelease = FirmwareRelease(),
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()
    fun hasSignalMetrics() = signalMetrics.isNotEmpty()
    fun hasPowerMetrics() = powerMetrics.isNotEmpty()
    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()
    fun hasPositionLogs() = positionLogs.isNotEmpty()
    fun hasHostMetrics() = hostMetrics.isNotEmpty()

    fun deviceMetricsFiltered(timeFrame: TimeFrame): List<Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return deviceMetrics.filter { it.time >= oldestTime }
    }

    fun signalMetricsFiltered(timeFrame: TimeFrame): List<MeshPacket> {
        val oldestTime = timeFrame.calculateOldestTime()
        return signalMetrics.filter { it.rxTime >= oldestTime }
    }

    fun powerMetricsFiltered(timeFrame: TimeFrame): List<Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return powerMetrics.filter { it.time >= oldestTime }
    }

    companion object {
        val Empty = MetricsState()
    }
}

/**
 * Supported time frames used to display data.
 */
@Suppress("MagicNumber")
enum class TimeFrame(
    val seconds: Long,
    @StringRes val strRes: Int
) {
    TWENTY_FOUR_HOURS(TimeUnit.DAYS.toSeconds(1), R.string.twenty_four_hours),
    FORTY_EIGHT_HOURS(TimeUnit.DAYS.toSeconds(2), R.string.forty_eight_hours),
    ONE_WEEK(TimeUnit.DAYS.toSeconds(7), R.string.one_week),
    TWO_WEEKS(TimeUnit.DAYS.toSeconds(14), R.string.two_weeks),
    FOUR_WEEKS(TimeUnit.DAYS.toSeconds(28), R.string.four_weeks),
    MAX(0L, R.string.max);

    fun calculateOldestTime(): Long = if (this == MAX) {
        MAX.seconds
    } else {
        System.currentTimeMillis() / 1000 - this.seconds
    }

    /**
     * The time interval to draw the vertical lines representing
     * time on the x-axis.
     *
     * @return seconds epoch seconds
     */
    fun lineInterval(): Long {
        return when (this.ordinal) {
            TWENTY_FOUR_HOURS.ordinal ->
                TimeUnit.HOURS.toSeconds(6)

            FORTY_EIGHT_HOURS.ordinal ->
                TimeUnit.HOURS.toSeconds(12)

            ONE_WEEK.ordinal,
            TWO_WEEKS.ordinal ->
                TimeUnit.DAYS.toSeconds(1)

            else ->
                TimeUnit.DAYS.toSeconds(7)
        }
    }

    /**
     * Used to detect a significant time separation between [Telemetry]s.
     */
    fun timeThreshold(): Long {
        return when (this.ordinal) {
            TWENTY_FOUR_HOURS.ordinal ->
                TimeUnit.HOURS.toSeconds(6)

            FORTY_EIGHT_HOURS.ordinal ->
                TimeUnit.HOURS.toSeconds(12)

            else ->
                TimeUnit.DAYS.toSeconds(1)
        }
    }

    /**
     * Calculates the needed [Dp] depending on the amount of time being plotted.
     *
     * @param time in seconds
     */
    fun dp(screenWidth: Int, time: Long): Dp {
        val timePerScreen = this.lineInterval()
        val multiplier = time / timePerScreen
        val dp = (screenWidth * multiplier).toInt().dp
        return dp.takeIf { it != 0.dp } ?: screenWidth.dp
    }
}

private fun MeshPacket.hasValidSignal(): Boolean =
    rxTime > 0 && (rxSnr != 0f && rxRssi != 0) && (hopStart > 0 && hopStart - hopLimit == 0)

private fun MeshPacket.toPosition(): Position? = if (!decoded.wantResponse) {
    runCatching { Position.parseFrom(decoded.payload) }.getOrNull()
} else {
    null
}

@Suppress("LongParameterList")
@HiltViewModel
class MetricsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    private val dispatchers: CoroutineDispatchers,
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val preferences: SharedPreferences,
) : ViewModel(), Logging {
    private val destNum = savedStateHandle.toRoute<NodesRoutes.NodeDetail>().destNum

    private fun MeshLog.hasValidTraceroute(): Boolean = with(fromRadio.packet) {
        hasDecoded() && decoded.wantResponse && from == 0 && to == destNum
    }

    /**
     * Creates a fallback node for hidden clients or nodes not yet in the database.
     * This prevents the detail screen from freezing when viewing unknown nodes.
     */
    private fun createFallbackNode(nodeNum: Int): Node {
        val userId = DataPacket.nodeNumToDefaultId(nodeNum)
        val safeUserId = userId.padStart(DEFAULT_ID_SUFFIX_LENGTH, '0').takeLast(DEFAULT_ID_SUFFIX_LENGTH)
        val longName = app.getString(R.string.fallback_node_name, safeUserId)
        val defaultUser = MeshProtos.User.newBuilder()
            .setId(userId)
            .setLongName(longName)
            .setShortName(safeUserId)
            .setHwModel(MeshProtos.HardwareModel.UNSET)
            .build()

        return Node(
            num = nodeNum,
            user = defaultUser,
        )
    }

    fun getUser(nodeNum: Int) = radioConfigRepository.getUser(nodeNum)
    val tileSource get() = CustomTileSource.getTileSource(preferences.getInt(MAP_STYLE_ID, 0))

    fun deleteLog(uuid: String) = viewModelScope.launch(dispatchers.io) {
        meshLogRepository.deleteLog(uuid)
    }

    fun clearPosition() = viewModelScope.launch(dispatchers.io) {
        destNum?.let {
            meshLogRepository.deleteLogs(it, PortNum.POSITION_APP_VALUE)
        }
    }

    fun onServiceAction(action: ServiceAction) = viewModelScope.launch {
        radioConfigRepository.onServiceAction(action)
    }

    private val _state = MutableStateFlow(MetricsState.Empty)
    val state: StateFlow<MetricsState> = _state

    private val _envState = MutableStateFlow(EnvironmentMetricsState())
    val environmentState: StateFlow<EnvironmentMetricsState> = _envState

    private val _timeFrame = MutableStateFlow(TimeFrame.TWENTY_FOUR_HOURS)
    val timeFrame: StateFlow<TimeFrame> = _timeFrame

    init {
        destNum?.let {
            radioConfigRepository.nodeDBbyNum
                .mapLatest { nodes -> nodes[destNum] to nodes.keys.firstOrNull() }
                .distinctUntilChanged()
                .onEach { (node, ourNode) ->
                    // Create a fallback node if not found in database (for hidden clients, etc.)
                    val actualNode = node ?: createFallbackNode(destNum)
                    val deviceHardware = actualNode.user.hwModel.number.let {
                        deviceHardwareRepository.getDeviceHardwareByModel(it)
                    }
                    _state.update { state ->
                        state.copy(
                            node = actualNode,
                            isLocal = destNum == ourNode,
                            deviceHardware = deviceHardware
                        )
                    }
                }.launchIn(viewModelScope)

            radioConfigRepository.deviceProfileFlow.onEach { profile ->
                val moduleConfig = profile.moduleConfig
                _state.update { state ->
                    state.copy(
                        isManaged = profile.config.security.isManaged,
                        isFahrenheit = moduleConfig.telemetry.environmentDisplayFahrenheit,
                    )
                }
            }.launchIn(viewModelScope)

            meshLogRepository.getTelemetryFrom(destNum).onEach { telemetry ->
                _state.update { state ->
                    state.copy(
                        deviceMetrics = telemetry.filter { it.hasDeviceMetrics() },
                        powerMetrics = telemetry.filter { it.hasPowerMetrics() },
                        hostMetrics = telemetry.filter { it.hasHostMetrics() },
                    )
                }
                _envState.update { state ->
                    state.copy(
                        environmentMetrics = telemetry.filter {
                            it.hasEnvironmentMetrics() &&
                                    it.environmentMetrics.relativeHumidity >= 0f &&
                                    !it.environmentMetrics.temperature.isNaN()
                        },
                    )
                }
            }.launchIn(viewModelScope)

            meshLogRepository.getMeshPacketsFrom(destNum).onEach { meshPackets ->
                _state.update { state ->
                    state.copy(signalMetrics = meshPackets.filter { it.hasValidSignal() })
                }
            }.launchIn(viewModelScope)

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
            }.launchIn(viewModelScope)

            meshLogRepository.getMeshPacketsFrom(destNum, PortNum.POSITION_APP_VALUE)
                .onEach { packets ->
                    val distinctPositions =
                        packets.mapNotNull { it.toPosition() }.asFlow()
                            .distinctUntilChanged { old, new ->
                                old.time == new.time ||
                                        (old.latitudeI == new.latitudeI && old.longitudeI == new.longitudeI)
                            }.toList()
                    _state.update { state ->
                        state.copy(positionLogs = distinctPositions)
                    }
                }.launchIn(viewModelScope)

            firmwareReleaseRepository.stableRelease.filterNotNull().onEach { latestStable ->
                _state.update { state ->
                    state.copy(latestStableFirmware = latestStable)
                }
            }.launchIn(viewModelScope)

            firmwareReleaseRepository.alphaRelease.filterNotNull().onEach { latestAlpha ->
                _state.update { state ->
                    state.copy(latestAlphaFirmware = latestAlpha)
                }
            }.launchIn(viewModelScope)

            debug("MetricsViewModel created")
        }
    }

    override fun onCleared() {
        super.onCleared()
        debug("MetricsViewModel cleared")
    }

    fun setTimeFrame(timeFrame: TimeFrame) {
        _timeFrame.value = timeFrame
    }

    /**
     * Write the persisted Position data out to a CSV file in the specified location.
     */
    fun savePositionCSV(uri: Uri) = viewModelScope.launch(dispatchers.main) {
        val positions = state.value.positionLogs
        writeToUri(uri) { writer ->
            writer.appendLine("\"date\",\"time\",\"latitude\",\"longitude\",\"altitude\",\"satsInView\",\"speed\",\"heading\"")

            val dateFormat =
                SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())

            positions.forEach { position ->
                val rxDateTime = dateFormat.format(position.time * 1000L)
                val latitude = position.latitudeI * 1e-7
                val longitude = position.longitudeI * 1e-7
                val altitude = position.altitude
                val satsInView = position.satsInView
                val speed = position.groundSpeed
                val heading = "%.2f".format(position.groundTrack * 1e-5)

                // date,time,latitude,longitude,altitude,satsInView,speed,heading
                writer.appendLine("$rxDateTime,\"$latitude\",\"$longitude\",\"$altitude\",\"$satsInView\",\"$speed\",\"$heading\"")
            }
        }
    }

    private suspend inline fun writeToUri(
        uri: Uri,
        crossinline block: suspend (BufferedWriter) -> Unit
    ) = withContext(dispatchers.io) {
        try {
            app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                    BufferedWriter(fileWriter).use { writer -> block.invoke(writer) }
                }
            }
        } catch (ex: FileNotFoundException) {
            errormsg("Can't write file error: ${ex.message}")
        }
    }
}
