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
package org.meshtastic.core.network.mqtt

import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.MapReport
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ServiceEnvelope

data class LatLon(val latitude: Double, val longitude: Double)

/** Broker topic strings and map-report payloads turned into per-root activity. */
object MqttTopicDiscovery {
    sealed interface Kind {
        /** `<root>/2/e/<channel>/!<hex>` */
        data class Packet(val channel: String, val gateway: String) : Kind

        /** `<root>/2/map/` */
        data object MapReport : Kind
    }

    data class Observation(val root: String, val kind: Kind)

    private val topicPattern = Regex("^(msh(?:/[^/]+)+)/2/(?:e/([^/]+)/(![0-9a-f]+)|map/)$")

    /**
     * Segments trimmed and empties dropped, the trailing "/" of a map topic kept. A root with a stray slash or space
     * cannot be saved, so its traffic is credited to the spelling that can. Repeated segments stay: "msh/US/US/FL" is a
     * real root with its own traffic.
     */
    fun normalizedTopic(topic: String): String {
        val trimmed = topic.trim()
        val joined = MqttRootTopic.segments(trimmed).joinToString("/")
        return if (trimmed.endsWith("/")) "$joined/" else joined
    }

    /** Null for anything that is not a `2/e` or `2/map` topic (uppercase hex, `/1/c/`, `/2/json/`, foreign roots). */
    fun observation(topic: String): Observation? {
        val match = topicPattern.matchEntire(normalizedTopic(topic)) ?: return null
        val (root, channel, gateway) = match.destructured
        return if (channel.isNotEmpty() && gateway.isNotEmpty()) {
            Observation(root, Kind.Packet(channel, gateway))
        } else {
            Observation(root, Kind.MapReport)
        }
    }

    data class MapReportSummary(
        /** `gateway_id`, or the sender's node id when that is empty. */
        val reporter: String,
        /** Null when both coordinates are zero, the firmware's "no position". */
        val position: LatLon?,
        val onlineLocalNodes: Int,
    )

    /** Plaintext MapReport out of an envelope; null for encrypted packets, other portnums and parse errors. */
    fun mapReport(envelope: ServiceEnvelope): MapReportSummary? {
        val packet = envelope.packet
        val data = packet?.decoded?.takeIf { it.portnum == PortNum.MAP_REPORT_APP }
        val report = data?.let { MapReport.ADAPTER.decodeOrNull(it.payload) }
        if (packet == null || report == null) return null
        val reporter = envelope.gateway_id.ifEmpty { NodeAddress.numToDefaultId(packet.from) }
        val position =
            if (report.latitude_i == 0 && report.longitude_i == 0) {
                null
            } else {
                LatLon(report.latitude_i / COORDINATE_SCALE, report.longitude_i / COORDINATE_SCALE)
            }
        return MapReportSummary(reporter, position, report.num_online_local_nodes)
    }

    /** Everything heard on one root during a sample, with every set capped. */
    class Activity {
        var packets = 0
            private set

        var mapReports = 0
            private set

        /** Sender ids from `MeshPacket.from` plus gateway ids. */
        val nodes = mutableSetOf<String>()

        /** Channel names from the topic. */
        val channels = mutableSetOf<String>()

        /** By reporter, last report wins. */
        val positions = mutableMapOf<String, LatLon>()

        /** Max `MapReport.num_online_local_nodes` seen. */
        var onlineLocalNodes = 0
            private set

        /** The region stream was unsubscribed at its cap, so the counts are a floor. */
        var truncated = false
            internal set

        val nodeCount: Int
            get() = nodes.size

        val messageCount: Int
            get() = packets + mapReports

        fun nearestDistanceMeters(from: LatLon?): Double? = from?.let { origin ->
            positions.values.minOfOrNull {
                latLongToMeter(origin.latitude, origin.longitude, it.latitude, it.longitude)
            }
        }

        internal fun record(kind: Kind, sender: String?) {
            when (kind) {
                is Kind.Packet -> {
                    packets++
                    channels.addCapped(kind.channel, MAX_CHANNELS)
                    nodes.addCapped(kind.gateway, MAX_NODES)
                    sender?.let { nodes.addCapped(it, MAX_NODES) }
                }

                Kind.MapReport -> mapReports++
            }
        }

        internal fun record(report: MapReportSummary) {
            nodes.addCapped(report.reporter, MAX_NODES)
            val position = report.position
            if (position != null && (report.reporter in positions || positions.size < MAX_POSITIONS)) {
                positions[report.reporter] = position
            }
            onlineLocalNodes = maxOf(onlineLocalNodes, report.onlineLocalNodes)
        }

        internal fun copy(): Activity = Activity().also {
            it.packets = packets
            it.mapReports = mapReports
            it.nodes += nodes
            it.channels += channels
            it.positions += positions
            it.onlineLocalNodes = onlineLocalNodes
            it.truncated = truncated
        }

        companion object {
            const val MAX_NODES = 256
            const val MAX_POSITIONS = 32
            const val MAX_CHANNELS = 32
        }
    }

    /** Bounded per-root tally. A new root past [MAX_ROOTS] is dropped and counted in [droppedRoots]. */
    class Tally {
        private val activity = mutableMapOf<String, Activity>()

        var droppedRoots = 0
            private set

        /**
         * Parses the topic, credits the root, reads the plaintext sender when the envelope parses and decodes a map
         * report on map topics. Returns the observation so the sampler can apply the region cap.
         */
        fun record(topic: String, payload: ByteArray): Observation? {
            val observation = observation(topic)
            when {
                observation == null -> Unit
                observation.root !in activity && activity.size >= MAX_ROOTS -> droppedRoots++
                else -> credit(observation, payload)
            }
            return observation
        }

        private fun credit(observation: Observation, payload: ByteArray) {
            val envelope =
                if (payload.size <= MAX_ENVELOPE_BYTES) ServiceEnvelope.ADAPTER.decodeOrNull(payload) else null
            val sender = envelope?.packet?.from?.takeIf { it != 0 }?.let(NodeAddress::numToDefaultId)
            val entry = activity.getOrPut(observation.root) { Activity() }
            entry.record(observation.kind, sender)
            if (observation.kind == Kind.MapReport && envelope != null) {
                mapReport(envelope)?.let(entry::record)
            }
        }

        fun markTruncated(root: String) {
            activity.getOrPut(root) { Activity() }.truncated = true
        }

        /** A copy, so a snapshot handed to the UI never changes under it. */
        fun snapshot(): Map<String, Activity> = activity.mapValues { it.value.copy() }

        companion object {
            const val MAX_ROOTS = 400

            /** An envelope is well under this; anything bigger is not one. */
            const val MAX_ENVELOPE_BYTES = 1_024
        }
    }

    private const val COORDINATE_SCALE = 1e7
}

private fun <T> MutableSet<T>.addCapped(value: T, cap: Int) {
    if (value in this || size < cap) add(value)
}
