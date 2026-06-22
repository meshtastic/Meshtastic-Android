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

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.snrLimit
import org.meshtastic.feature.car.model.CarLocalStats
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality
import org.meshtastic.feature.car.service.MessageSnapshot
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
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

    // Preset-relative SNR band offsets (dB) around the modem preset's demodulation floor; aligned with core/ui
    // LoraSignalIndicator. EXCELLENT sits a margin above the floor; FAIR/BAD step below it.
    private const val SNR_EXCELLENT_MARGIN = 5.5f
    private const val SNR_FAIR_OFFSET = 5.5f
    private const val SNR_BAD_OFFSET = 7.5f

    /** Converts a [Node] to a [NodeUi] for car display. */
    fun buildNodeUi(node: Node, modemPreset: ModemPreset? = null): NodeUi = NodeUi(
        nodeNum = node.num,
        userId = node.user.id,
        longName = node.user.long_name.ifEmpty { "Unknown" },
        shortName = node.user.short_name.ifEmpty { "?" },
        signalQuality = determineSignalQuality(node.snr, modemPreset),
        batteryPercent = node.batteryLevel?.takeIf { it in 1..BATTERY_MAX_PERCENT },
        isOnline = node.isOnline,
        lastHeard = node.lastHeard.toLong() * SECONDS_TO_MILLIS,
        hasPosition = node.validPosition != null,
    )

    /** Sorts nodes for car display: online nodes first, then by lastHeard descending. */
    fun sortNodes(nodes: Collection<Node>, modemPreset: ModemPreset? = null): List<NodeUi> = nodes
        .map { buildNodeUi(it, modemPreset) }
        .sortedWith(compareByDescending<NodeUi> { it.isOnline }.thenByDescending { it.lastHeard })

    /** Builds ordered conversation list: sorted by most recent message time descending. */
    fun sortConversations(conversations: List<ConversationUi>): List<ConversationUi> =
        conversations.sortedByDescending { it.lastMessageTime }

    /**
     * Determines signal quality from SNR relative to the modem preset's demodulation floor ([ModemPreset.snrLimit]).
     * RSSI is not used (matching core/ui); a null/unknown preset falls back to the LongFast default limit.
     */
    fun determineSignalQuality(snr: Float, modemPreset: ModemPreset? = null): SignalQuality {
        if (snr == Float.MAX_VALUE) return SignalQuality.NONE
        val limit = modemPreset.snrLimit
        return when {
            snr > limit + SNR_EXCELLENT_MARGIN -> SignalQuality.EXCELLENT
            snr > limit -> SignalQuality.GOOD
            snr > limit - SNR_FAIR_OFFSET -> SignalQuality.FAIR
            snr >= limit - SNR_BAD_OFFSET -> SignalQuality.BAD
            else -> SignalQuality.NONE
        }
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
