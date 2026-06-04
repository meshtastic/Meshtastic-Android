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
package org.meshtastic.feature.car.util

import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Node
import org.meshtastic.feature.car.model.CarLocalStats
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality
import org.meshtastic.feature.car.service.MessageSnapshot
import org.meshtastic.proto.LocalStats

/**
 * Pure-function helpers that convert domain models into car UI models.
 *
 * All methods are free of Car App Library dependencies, making them testable as plain JVM unit tests without
 * Robolectric.
 */
internal object CarScreenDataBuilder {

    private const val SECONDS_TO_MILLIS = 1000L
    private const val MINUTE_SECONDS = 60
    private const val HOUR_SECONDS = 3600
    private const val DAY_SECONDS = 86400
    private const val BATTERY_MAX_PERCENT = 100

    // Thresholds aligned with core/ui LoraSignalIndicator.kt
    private const val SNR_GOOD_THRESHOLD = -7f
    private const val SNR_FAIR_THRESHOLD = -15f
    private const val RSSI_GOOD_THRESHOLD = -115
    private const val RSSI_FAIR_THRESHOLD = -126

    /** Converts a [Node] to a [NodeUi] for car display. */
    fun buildNodeUi(node: Node): NodeUi = NodeUi(
        nodeNum = node.num,
        userId = node.user.id,
        longName = node.user.long_name.ifEmpty { "Unknown" },
        shortName = node.user.short_name.ifEmpty { "?" },
        signalQuality = determineSignalQuality(node.snr, node.rssi),
        batteryPercent = node.batteryLevel?.takeIf { it in 1..BATTERY_MAX_PERCENT },
        isOnline = node.isOnline,
        lastHeard = node.lastHeard.toLong() * SECONDS_TO_MILLIS,
        hasPosition = node.validPosition != null,
    )

    /** Sorts nodes for car display: online nodes first, then by lastHeard descending. */
    fun sortNodes(nodes: Collection<Node>): List<NodeUi> = nodes
        .map(::buildNodeUi)
        .sortedWith(compareByDescending<NodeUi> { it.isOnline }.thenByDescending { it.lastHeard })

    /** Builds ordered conversation list: sorted by most recent message time descending. */
    fun sortConversations(conversations: List<ConversationUi>): List<ConversationUi> =
        conversations.sortedByDescending { it.lastMessageTime }

    /** Determines signal quality from SNR and RSSI values. */
    fun determineSignalQuality(snr: Float, rssi: Int): SignalQuality = when {
        snr == Float.MAX_VALUE || rssi == Int.MAX_VALUE -> SignalQuality.NONE
        snr > SNR_GOOD_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD -> SignalQuality.EXCELLENT
        snr > SNR_GOOD_THRESHOLD && rssi > RSSI_FAIR_THRESHOLD -> SignalQuality.GOOD
        snr > SNR_FAIR_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD -> SignalQuality.GOOD
        snr > SNR_FAIR_THRESHOLD -> SignalQuality.FAIR
        else -> SignalQuality.BAD
    }

    /**
     * Builds a [CarLocalStats] snapshot from the device's [Node], [LocalStats], and node DB. Falls back to
     * Node.deviceMetrics when LocalStats hasn't been populated yet.
     */
    @Suppress("MagicNumber")
    fun buildLocalStats(ourNode: Node?, stats: LocalStats, allNodes: Collection<Node>): CarLocalStats {
        val metrics = ourNode?.deviceMetrics
        val hasStats = stats.uptime_seconds != 0
        return CarLocalStats(
            batteryLevel = metrics?.battery_level ?: 0,
            hasBattery = metrics?.battery_level != null,
            channelUtilization = if (hasStats) stats.channel_utilization else metrics?.channel_utilization ?: 0f,
            airUtilization = if (hasStats) stats.air_util_tx else metrics?.air_util_tx ?: 0f,
            totalNodes = allNodes.size,
            onlineNodes = allNodes.count { it.isOnline },
            uptimeSeconds = if (hasStats) stats.uptime_seconds else metrics?.uptime_seconds ?: 0,
            numPacketsTx = stats.num_packets_tx,
            numPacketsRx = stats.num_packets_rx,
        )
    }

    /** Formats uptime seconds as a human-readable string. */
    fun formatUptime(seconds: Int): String {
        val days = seconds / DAY_SECONDS
        val hours = (seconds % DAY_SECONDS) / HOUR_SECONDS
        val minutes = (seconds % HOUR_SECONDS) / MINUTE_SECONDS
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    /**
     * Returns the contact key in the format expected by the messaging system. Channels use `"<channelIndex>^all"`
     * format; DMs use `"0<nodeId>"`.
     */
    fun buildContactKey(channelIndex: Int): String = "${channelIndex}${NodeAddress.ID_BROADCAST}"

    /** Returns the most recent N messages from a list, ordered chronologically (oldest first). */
    fun recentMessages(messages: List<MessageSnapshot>, limit: Int = MAX_CONVERSATION_MESSAGES): List<MessageSnapshot> =
        messages.takeLast(limit)

    /** Maximum messages to include in a ConversationItem. */
    const val MAX_CONVERSATION_MESSAGES = 5

    /** Maximum conversations to display in the messaging list. */
    const val MAX_CONVERSATIONS = 10
}
