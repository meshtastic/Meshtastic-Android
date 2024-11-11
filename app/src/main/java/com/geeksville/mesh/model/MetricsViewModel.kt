package com.geeksville.mesh.model

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.Position
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.ui.Route
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
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class MetricsState(
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val displayUnits: DisplayUnits = DisplayUnits.METRIC,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val environmentMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshPacket> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()
    fun hasEnvironmentMetrics() = environmentMetrics.isNotEmpty()
    fun hasSignalMetrics() = signalMetrics.isNotEmpty()
    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()
    fun hasPositionLogs() = positionLogs.isNotEmpty()

    companion object {
        val Empty = MetricsState()
    }
}

/**
 * Supported time frames used to display data.
 */
@Suppress("MagicNumber")
enum class TimeFrame(
    val milliseconds: Long,
    @StringRes val strRes: Int
) {
    TWENTY_FOUR_HOURS(86400000L, R.string.twenty_four_hours),
    FORTY_EIGHT_HOURS(172800000L, R.string.forty_eight_hours),
    ONE_WEEK(604800000L, R.string.one_week),
    TWO_WEEKS(1209600000L, R.string.two_weeks),
    ONE_MONTH(2629800000L, R.string.one_month),
    MAX(0L, R.string.max);

    fun calculateOldestTime(): Long = if (this == MAX) {
        MAX.milliseconds
    } else {
        System.currentTimeMillis() - this.milliseconds
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
) : ViewModel(), Logging {
    private val destNum = savedStateHandle.toRoute<Route.NodeDetail>().destNum

    private fun MeshLog.hasValidTraceroute(): Boolean = with(fromRadio.packet) {
        hasDecoded() && decoded.wantResponse && from == 0 && to == destNum
    }

    fun getUser(nodeNum: Int) = radioConfigRepository.getUser(nodeNum)

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
        _timeFrame.flatMapLatest { timeFrame ->
            meshLogRepository.getTelemetryFrom(destNum, timeFrame.calculateOldestTime()).onEach { telemetry ->
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
        _timeFrame.flatMapLatest { timeFrame ->
            val oldestTime = timeFrame.calculateOldestTime()
            meshLogRepository.getMeshPacketsFrom(destNum, oldestTime = oldestTime).onEach { meshPackets ->
                _state.update { state ->
                    state.copy(signalMetrics = meshPackets.filter { it.hasValidSignal() })
                }
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
            _state.update { state -> state.copy(positionLogs = packets.mapNotNull { it.toPosition() }) }
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
}
