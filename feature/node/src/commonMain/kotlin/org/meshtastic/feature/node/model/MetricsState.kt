/*
 * Copyright (c) 2026 Meshtastic LLC
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

import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.DeviceLink
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.rxTimeOrNull
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry

data class MetricsState(
    val isLocal: Boolean = false,
    val isManaged: Boolean = true,
    /**
     * Whether the connected radio has the neighbor-info module enabled. Firmware drops every neighbor-info packet on a
     * node with it off, including the reply to its own request, so the request is offered only when it is on.
     */
    val canRequestNeighborInfo: Boolean = false,
    val isFahrenheit: Boolean = false,
    val displayUnits: MeasurementSystem = MeasurementSystem.METRIC,
    val node: Node? = null,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val localStats: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val powerMetrics: List<Telemetry> = emptyList(),
    val hostMetrics: List<Telemetry> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshLog> = emptyList(),
    /** Responses from this node to traces our radio sent without this app asking. */
    val tracerouteObserved: List<MeshLog> = emptyList(),
    /** Traceroute requests this node sent to us, each carrying its inbound route. */
    val tracerouteTracedBy: List<MeshLog> = emptyList(),
    val neighborInfoRequests: List<MeshLog> = emptyList(),
    val neighborInfoResults: List<MeshLog> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
    /** msh.to vendor/marketplace links for this device's hardware, region-filtered and sorted (vendor first). */
    val deviceLinks: List<DeviceLink> = emptyList(),
    /** Whether the flasher serves a 3D model for [deviceHardware]'s hero illustration. */
    val hasDeviceModel: Boolean = false,
    val firmwareEdition: FirmwareEdition? = null,
    val latestStableFirmware: FirmwareRelease = FirmwareRelease(),
    val latestAlphaFirmware: FirmwareRelease = FirmwareRelease(),
    val paxMetrics: List<MeshLog> = emptyList(),
    val airQualityMetrics: List<Telemetry> = emptyList(),
    /** The PlatformIO environment reported by the device (if known). */
    val reportedTarget: String? = null,
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()

    fun hasSignalMetrics() = signalMetrics.isNotEmpty()

    fun hasLocalStats() = localStats.isNotEmpty()

    fun hasPowerMetrics() = powerMetrics.isNotEmpty()

    fun hasTracerouteLogs() =
        tracerouteRequests.isNotEmpty() || tracerouteObserved.isNotEmpty() || tracerouteTracedBy.isNotEmpty()

    fun hasNeighborInfoLogs() = neighborInfoRequests.isNotEmpty() || neighborInfoResults.isNotEmpty()

    fun hasPositionLogs() = positionLogs.isNotEmpty()

    fun hasHostMetrics() = hostMetrics.isNotEmpty()

    fun hasPaxMetrics() = paxMetrics.isNotEmpty()

    fun hasAirQualityMetrics() = airQualityMetrics.isNotEmpty()

    /** Finds the oldest timestamp (in seconds) among all collected metric types. */
    @Suppress("MagicNumber")
    fun oldestTimestampSeconds(): Long? {
        val telemetryTimes =
            (deviceMetrics + localStats + powerMetrics + hostMetrics + airQualityMetrics).map { it.time.toLong() }
        // Unstamped packets carry no timestamp to compare — dropping them beats letting the epoch win the min.
        val signalTimes = signalMetrics.mapNotNull { it.rxTimeOrNull()?.toLong() }
        val logTimes =
            (tracerouteRequests + tracerouteResults + neighborInfoRequests + neighborInfoResults + paxMetrics).map {
                it.received_date / 1000L
            }
        val positionTimes = positionLogs.map { it.time.toLong() }

        val allTimes = telemetryTimes + signalTimes + logTimes + positionTimes
        return allTimes.minOrNull()
    }

    companion object {
        val Empty = MetricsState()
    }
}

/**
 * Every request with the reply that answered it, plus neighbor info the node sent on its own - a broadcast, or the
 * connected radio's periodic report to the app - newest first.
 */
fun MetricsState.neighborInfoEntries(): List<NeighborInfoEntry> {
    val requested = neighborInfoRequests.map { request ->
        val id = request.fromRadio.packet?.id
        NeighborInfoEntry.Requested(
            log = request,
            reply = neighborInfoResults.find { it.fromRadio.packet?.decoded?.request_id == id },
        )
    }
    // Only the report each request took is excluded, so a second copy answering the same request still shows.
    val pairedReplies = requested.mapNotNull { it.reply?.uuid }.toSet()
    val received = neighborInfoResults.filter { it.uuid !in pairedReplies }.map { NeighborInfoEntry.Received(it) }
    return (requested + received).sortedByDescending { it.timeSeconds }
}

/** One row of the neighbor-info log. */
sealed interface NeighborInfoEntry {
    val log: MeshLog

    /** The packet carrying the neighbor list, or null for a request nothing answered. */
    val report: MeshLog?

    /** When the neighbor list arrived, or when the request went out if it never did. */
    val timeSeconds: Double
        get() = (report ?: log).received_date / MS_PER_SEC_DOUBLE

    /** A request we sent, with the reply if one came back. */
    data class Requested(override val log: MeshLog, val reply: MeshLog?) : NeighborInfoEntry {
        override val report: MeshLog?
            get() = reply
    }

    /** Neighbor info that arrived without our asking. */
    data class Received(override val log: MeshLog) : NeighborInfoEntry {
        override val report: MeshLog
            get() = log
    }
}

private const val MS_PER_SEC_DOUBLE = 1000.0
