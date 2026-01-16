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
package org.meshtastic.feature.node.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.forty_eight_hours
import org.meshtastic.core.strings.four_weeks
import org.meshtastic.core.strings.max
import org.meshtastic.core.strings.one_week
import org.meshtastic.core.strings.twenty_four_hours
import org.meshtastic.core.strings.two_weeks
import org.meshtastic.proto.Config
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import java.util.concurrent.TimeUnit

data class MetricsState(
    val isLocal: Boolean = false,
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val displayUnits: Config.DisplayConfig.DisplayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
    val node: Node? = null,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val powerMetrics: List<Telemetry> = emptyList(),
    val hostMetrics: List<Telemetry> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshLog> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
    val isLocalDevice: Boolean = false,
    val firmwareEdition: FirmwareEdition? = null,
    val latestStableFirmware: FirmwareRelease = FirmwareRelease(),
    val latestAlphaFirmware: FirmwareRelease = FirmwareRelease(),
    val paxMetrics: List<MeshLog> = emptyList(),
    /** The PlatformIO environment reported by the device (if known). */
    val reportedTarget: String? = null,
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()

    fun hasSignalMetrics() = signalMetrics.isNotEmpty()

    fun hasPowerMetrics() = powerMetrics.isNotEmpty()

    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()

    fun hasPositionLogs() = positionLogs.isNotEmpty()

    fun hasHostMetrics() = hostMetrics.isNotEmpty()

    fun hasPaxMetrics() = paxMetrics.isNotEmpty()

    fun deviceMetricsFiltered(timeFrame: TimeFrame): List<Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return deviceMetrics.filter { (it.time ?: 0) >= oldestTime }
    }

    fun signalMetricsFiltered(timeFrame: TimeFrame): List<MeshPacket> {
        val oldestTime = timeFrame.calculateOldestTime()
        return signalMetrics.filter { (it.rx_time ?: 0) >= oldestTime }
    }

    fun powerMetricsFiltered(timeFrame: TimeFrame): List<Telemetry> {
        val oldestTime = timeFrame.calculateOldestTime()
        return powerMetrics.filter { (it.time ?: 0) >= oldestTime }
    }

    companion object {
        val Empty = MetricsState()
    }
}

/** Supported time frames used to display data. */
@Suppress("MagicNumber")
enum class TimeFrame(val seconds: Long, val strRes: StringResource) {
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
