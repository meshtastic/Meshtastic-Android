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

import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.proto.Config
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry

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
    val neighborInfoRequests: List<MeshLog> = emptyList(),
    val neighborInfoResults: List<MeshLog> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
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

    fun hasNeighborInfoLogs() = neighborInfoRequests.isNotEmpty()

    fun hasPositionLogs() = positionLogs.isNotEmpty()

    fun hasHostMetrics() = hostMetrics.isNotEmpty()

    fun hasPaxMetrics() = paxMetrics.isNotEmpty()

    companion object {
        val Empty = MetricsState()
    }
}
