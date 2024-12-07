/*
 * Copyright (c) 2024 Meshtastic LLC
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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.MeshProtos.HardwareModel
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.Position
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.ui.Route
import com.geeksville.mesh.ui.map.MAP_STYLE_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class MetricsState(
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val displayUnits: DisplayUnits = DisplayUnits.METRIC,
    val node: NodeEntity? = null,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val environmentMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshPacket> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
    @DrawableRes val deviceImageRes: Int = R.drawable.hw_unknown,
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()
    fun hasSignalMetrics() = signalMetrics.isNotEmpty()
    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()
    fun hasPositionLogs() = positionLogs.isNotEmpty()

    fun deviceMetricsFiltered(timeFrame: TimeFrame): List<Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return deviceMetrics.filter { it.time >= oldestTime }
    }

    fun environmentMetricsFiltered(timeFrame: TimeFrame): List<Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return environmentMetrics.filter { it.time >= oldestTime }
    }

    fun signalMetricsFiltered(timeFrame: TimeFrame): List<MeshPacket> {
        val oldestTime = timeFrame.calculateOldestTime()
        return signalMetrics.filter { it.rxTime >= oldestTime }
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
            TWENTY_FOUR_HOURS.ordinal,
            FORTY_EIGHT_HOURS.ordinal ->
                TimeUnit.HOURS.toSeconds(1)
            ONE_WEEK.ordinal,
            TWO_WEEKS.ordinal ->
                TimeUnit.DAYS.toSeconds(1)
            else ->
                TimeUnit.DAYS.toSeconds(7)
        }
    }

    /**
     * Calculates the needed [Dp] depending on the amount of time being plotted.
     *
     * @param time in seconds
     */
    fun dp(screenWidth: Int, time: Long): Dp {

        val timePerScreen = when (this.ordinal) {
            TWENTY_FOUR_HOURS.ordinal,
            FORTY_EIGHT_HOURS.ordinal ->
                TimeUnit.HOURS.toSeconds(1)
            ONE_WEEK.ordinal,
            TWO_WEEKS.ordinal ->
                TimeUnit.DAYS.toSeconds(1)
            else ->
                TimeUnit.DAYS.toSeconds(7)
        }

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

@HiltViewModel
class MetricsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    private val dispatchers: CoroutineDispatchers,
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val preferences: SharedPreferences,
) : ViewModel(), Logging {
    private val destNum = savedStateHandle.toRoute<Route.NodeDetail>().destNum

    private fun MeshLog.hasValidTraceroute(): Boolean = with(fromRadio.packet) {
        hasDecoded() && decoded.wantResponse && from == 0 && to == destNum
    }

    fun getUser(nodeNum: Int) = radioConfigRepository.getUser(nodeNum)
    val tileSource get() = CustomTileSource.getTileSource(preferences.getInt(MAP_STYLE_ID, 0))

    fun deleteLog(uuid: String) = viewModelScope.launch(dispatchers.io) {
        meshLogRepository.deleteLog(uuid)
    }

    fun clearPosition() = viewModelScope.launch(dispatchers.io) {
        meshLogRepository.deleteLogs(destNum, PortNum.POSITION_APP_VALUE)
    }

    private val _state = MutableStateFlow(MetricsState.Empty)
    val state: StateFlow<MetricsState> = _state

    private val _timeFrame = MutableStateFlow(TimeFrame.TWENTY_FOUR_HOURS)
    val timeFrame: StateFlow<TimeFrame> = _timeFrame

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        radioConfigRepository.nodeDBbyNum
            .mapLatest { nodes -> nodes[destNum] }
            .distinctUntilChanged()
            .onEach { node ->
                _state.update { state -> state.copy(node = node) }
                node?.user?.hwModel?.let { hwModel ->
                    _state.update { state ->
                        state.copy(
                            deviceHardware = getDeviceHardwareFromHardwareModel(hwModel),
                            deviceImageRes = getDeviceVectorImageFromHardwareModel(hwModel)
                        )
                    }
                }
            }
            .launchIn(viewModelScope)

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
                    environmentMetrics = telemetry.filter {
                        it.hasEnvironmentMetrics() && it.environmentMetrics.relativeHumidity >= 0f
                    }
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

        meshLogRepository.getMeshPacketsFrom(destNum, PortNum.POSITION_APP_VALUE).onEach { packets ->
            val distinctPositions =
                packets.mapNotNull { it.toPosition() }.asFlow().distinctUntilChanged { old, new ->
                    old.time == new.time || (old.latitudeI == new.latitudeI && old.longitudeI == new.longitudeI)
                }.toList()
            _state.update { state ->
                state.copy(positionLogs = distinctPositions)
            }
        }.launchIn(viewModelScope)

        debug("MetricsViewModel created")
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

    private var deviceHardwareList: List<DeviceHardware> = listOf()
    private fun getDeviceHardwareFromHardwareModel(
        hwModel: HardwareModel
    ): DeviceHardware? {
        if (deviceHardwareList.isEmpty()) {
            try {
                val json =
                    app.assets.open("device_hardware.json").bufferedReader().use { it.readText() }
                deviceHardwareList = Json.decodeFromString<List<DeviceHardware>>(json)
            } catch (ex: IOException) {
                errormsg("Can't read device_hardware.json error: ${ex.message}")
            }
        }
        return deviceHardwareList.find { it.hwModel == hwModel.number }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun getDeviceVectorImageFromHardwareModel(hwModel: HardwareModel): Int {
        return when (hwModel) {
            HardwareModel.DIY_V1 -> R.drawable.hw_diy
            HardwareModel.HELTEC_HT62 -> R.drawable.hw_heltec_ht62_esp32c3_sx1262
            HardwareModel.HELTEC_MESH_NODE_T114 -> R.drawable.hw_heltec_mesh_node_t114
            HardwareModel.HELTEC_V3 -> R.drawable.hw_heltec_v3
            HardwareModel.HELTEC_VISION_MASTER_E213 -> R.drawable.hw_heltec_vision_master_e213
            HardwareModel.HELTEC_VISION_MASTER_E290 -> R.drawable.hw_heltec_vision_master_e290
            HardwareModel.HELTEC_VISION_MASTER_T190 -> R.drawable.hw_heltec_vision_master_t190
            HardwareModel.HELTEC_WIRELESS_PAPER -> R.drawable.hw_heltec_wireless_paper
            HardwareModel.HELTEC_WIRELESS_TRACKER -> R.drawable.hw_heltec_wireless_tracker
            HardwareModel.HELTEC_WIRELESS_TRACKER_V1_0 -> R.drawable.hw_heltec_wireless_tracker_v1_0
            HardwareModel.HELTEC_WSL_V3 -> R.drawable.hw_heltec_wsl_v3
            HardwareModel.NANO_G2_ULTRA -> R.drawable.hw_nano_g2_ultra
            HardwareModel.RPI_PICO -> R.drawable.hw_pico
            HardwareModel.NRF52_PROMICRO_DIY -> R.drawable.hw_promicro
            HardwareModel.RAK11310 -> R.drawable.hw_rak11310
            HardwareModel.RAK4631 -> R.drawable.hw_rak4631
            HardwareModel.RPI_PICO2 -> R.drawable.hw_rpipicow
            HardwareModel.SENSECAP_INDICATOR -> R.drawable.hw_seeed_sensecap_indicator
            HardwareModel.SEEED_XIAO_S3 -> R.drawable.hw_seeed_xiao_s3
            HardwareModel.STATION_G2 -> R.drawable.hw_station_g2
            HardwareModel.T_DECK -> R.drawable.hw_t_deck
            HardwareModel.T_ECHO -> R.drawable.hw_t_echo
            HardwareModel.T_WATCH_S3 -> R.drawable.hw_t_watch_s3
            HardwareModel.TBEAM -> R.drawable.hw_tbeam
            HardwareModel.LILYGO_TBEAM_S3_CORE -> R.drawable.hw_tbeam_s3_core
            HardwareModel.TLORA_C6 -> R.drawable.hw_tlora_c6
            HardwareModel.TLORA_T3_S3 -> R.drawable.hw_tlora_t3s3_v1
            HardwareModel.TLORA_V2_1_1P6 -> R.drawable.hw_tlora_v2_1_1_6
            HardwareModel.TLORA_V2_1_1P8 -> R.drawable.hw_tlora_v2_1_1_8
            HardwareModel.TRACKER_T1000_E -> R.drawable.hw_tracker_t1000_e
            HardwareModel.WIO_WM1110 -> R.drawable.hw_wio_tracker_wm1110
            HardwareModel.WISMESH_TAP -> R.drawable.hw_rak_wismeshtap
            else -> R.drawable.hw_unknown
        }
    }
}
