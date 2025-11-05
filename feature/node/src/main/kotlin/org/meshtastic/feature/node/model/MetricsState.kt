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

package org.meshtastic.feature.node.model

import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.TelemetryProtos
import java.util.concurrent.TimeUnit
import org.meshtastic.core.strings.R as Res

data class MetricsState(
    val isLocal: Boolean = false,
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits =
        ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC,
    val node: Node? = null,
    val deviceMetrics: List<TelemetryProtos.Telemetry> = emptyList(),
    val signalMetrics: List<MeshProtos.MeshPacket> = emptyList(),
    val powerMetrics: List<TelemetryProtos.Telemetry> = emptyList(),
    val hostMetrics: List<TelemetryProtos.Telemetry> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshLog> = emptyList(),
    val positionLogs: List<MeshProtos.Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
    val isLocalDevice: Boolean = false,
    val firmwareEdition: MeshProtos.FirmwareEdition? = null,
    val latestStableFirmware: FirmwareRelease = FirmwareRelease(),
    val latestAlphaFirmware: FirmwareRelease = FirmwareRelease(),
    val paxMetrics: List<MeshLog> = emptyList(),
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()

    fun hasSignalMetrics() = signalMetrics.isNotEmpty()

    fun hasPowerMetrics() = powerMetrics.isNotEmpty()

    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()

    fun hasPositionLogs() = positionLogs.isNotEmpty()

    fun hasHostMetrics() = hostMetrics.isNotEmpty()

    fun hasPaxMetrics() = paxMetrics.isNotEmpty()

    fun deviceMetricsFiltered(timeFrame: TimeFrame): List<TelemetryProtos.Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return deviceMetrics.filter { it.time >= oldestTime }
    }

    fun signalMetricsFiltered(timeFrame: TimeFrame): List<MeshProtos.MeshPacket> {
        val oldestTime = timeFrame.calculateOldestTime()
        return signalMetrics.filter { it.rxTime >= oldestTime }
    }

    fun powerMetricsFiltered(timeFrame: TimeFrame): List<TelemetryProtos.Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return powerMetrics.filter { it.time >= oldestTime }
    }

    companion object {
        val Empty = MetricsState()
    }
}

/** Supported time frames used to display data. */
@Suppress("MagicNumber")
enum class TimeFrame(val seconds: Long, @StringRes val strRes: Int) {
    TWENTY_FOUR_HOURS(TimeUnit.DAYS.toSeconds(1), Res.string.twenty_four_hours),
    FORTY_EIGHT_HOURS(TimeUnit.DAYS.toSeconds(2), Res.string.forty_eight_hours),
    ONE_WEEK(TimeUnit.DAYS.toSeconds(7), Res.string.one_week),
    TWO_WEEKS(TimeUnit.DAYS.toSeconds(14), Res.string.two_weeks),
    FOUR_WEEKS(TimeUnit.DAYS.toSeconds(28), Res.string.four_weeks),
    MAX(0L, Res.string.max),
    ;

    fun calculateOldestTime(): Long = if (this == MAX) {
        MAX.seconds
    } else {
        System.currentTimeMillis() / 1000 - this.seconds
    }

    /**
     * The time interval to draw the vertical lines representing time on the x-axis.
     *
     * @return seconds epoch seconds
     */
    fun lineInterval(): Long = when (this.ordinal) {
        TWENTY_FOUR_HOURS.ordinal -> TimeUnit.HOURS.toSeconds(6)

        FORTY_EIGHT_HOURS.ordinal -> TimeUnit.HOURS.toSeconds(12)

        ONE_WEEK.ordinal,
        TWO_WEEKS.ordinal,
        -> TimeUnit.DAYS.toSeconds(1)

        else -> TimeUnit.DAYS.toSeconds(7)
    }

    /** Used to detect a significant time separation between [TelemetryProtos.Telemetry]s. */
    fun timeThreshold(): Long = when (this.ordinal) {
        TWENTY_FOUR_HOURS.ordinal -> TimeUnit.HOURS.toSeconds(6)

        FORTY_EIGHT_HOURS.ordinal -> TimeUnit.HOURS.toSeconds(12)

        else -> TimeUnit.DAYS.toSeconds(1)
    }

    /**
     * Calculates the needed [androidx.compose.ui.unit.Dp] depending on the amount of time being plotted.
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
