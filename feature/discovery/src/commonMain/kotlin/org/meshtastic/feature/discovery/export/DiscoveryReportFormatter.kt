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
package org.meshtastic.feature.discovery.export

import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.feature.discovery.ui.formatDuration

internal object DiscoveryReportFormatter {

    fun formatSessionDate(session: DiscoverySessionEntity): String = DateFormatter.formatDateTime(session.timestamp)

    fun formatSessionOverviewLines(session: DiscoverySessionEntity): List<Pair<String, String>> = listOf(
        "Date" to formatSessionDate(session),
        "Total unique nodes" to session.totalUniqueNodes.toString(),
        "Total dwell time" to formatDuration(session.totalDwellSeconds),
        "Status" to session.completionStatus.replaceFirstChar { it.uppercase() },
        "Channel utilization" to "${NumberFormatter.format(session.avgChannelUtilization, 1)}%",
        "Total messages" to session.totalMessages.toString(),
        "Total sensor packets" to session.totalSensorPackets.toString(),
    )

    fun formatPresetLines(result: DiscoveryPresetResultEntity): List<Pair<String, String>> = buildList {
        add("Unique nodes" to result.uniqueNodes.toString())
        add("Direct neighbors" to result.directNeighborCount.toString())
        add("Mesh neighbors" to result.meshNeighborCount.toString())
        add("Dwell time" to formatDuration(result.dwellDurationSeconds))
        add("Channel utilization" to "${NumberFormatter.format(result.avgChannelUtilization, 1)}%")
        add("Airtime rate" to "${NumberFormatter.format(result.avgAirtimeRate, 1)}%")
        add("Packet success" to "${NumberFormatter.format(result.packetSuccessRate, 1)}%")
        add("Messages" to result.messageCount.toString())
        add("Packets TX" to result.numPacketsTx.toString())
        add("Packets RX" to result.numPacketsRx.toString())
        val aiText = result.aiSummary
        if (!aiText.isNullOrBlank()) {
            add("Analysis" to aiText)
        }
    }

    fun formatNodeLine(node: DiscoveredNodeEntity): String = buildString {
        append(node.longName ?: node.shortName ?: "!${node.nodeNum.toString(radix = 16)}")
        append(" | ${node.neighborType}")
        append(" | SNR: ${NumberFormatter.format(node.snr, 1)}")
        append(" | RSSI: ${node.rssi}")
        val distance = node.distanceFromUser
        if (distance != null) {
            append(" | ${NumberFormatter.format(distance, 0)}m")
        }
    }

    fun generateFileName(session: DiscoverySessionEntity, extension: String): String {
        val dateStr =
            DateFormatter.formatDateTime(session.timestamp).replace(" ", "_").replace("/", "-").replace(":", "-")
        return "meshtastic_discovery_$dateStr.$extension"
    }
}
