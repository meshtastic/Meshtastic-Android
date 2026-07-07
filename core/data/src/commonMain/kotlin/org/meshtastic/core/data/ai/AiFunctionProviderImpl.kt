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
package org.meshtastic.core.data.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of [AiFunctionProvider] that bridges AI function invocations to existing Meshtastic repositories and
 * use cases.
 */
@Suppress("TooManyFunctions")
@Single(binds = [AiFunctionProvider::class])
class AiFunctionProviderImpl(
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val fuzzyNameResolver: FuzzyNameResolver,
    private val rateLimiter: RateLimiter,
    private val clock: Clock,
) : AiFunctionProvider {

    override suspend fun sendMessage(text: String, recipientName: String?, channelName: String?): SendMessageResult =
        withTimeout(OPERATION_TIMEOUT) {
            // Check connection
            if (serviceRepository.connectionState.value != ConnectionState.Connected) {
                return@withTimeout SendMessageResult.NotConnected(
                    "Not connected to a Meshtastic radio. Please connect first.",
                )
            }

            // Check rate limit
            when (val rateResult = rateLimiter.tryAcquire()) {
                is RateLimitResult.Permitted -> {
                    /* proceed */
                }

                is RateLimitResult.Limited -> {
                    return@withTimeout SendMessageResult.RateLimited(rateResult.retryAfterSeconds)
                }
            }

            // Validate message length
            val messageBytes = text.encodeToByteArray()
            if (messageBytes.size > MAX_MESSAGE_LENGTH) {
                return@withTimeout SendMessageResult.InvalidArgument(
                    "Message too long: ${messageBytes.size} bytes exceeds maximum of $MAX_MESSAGE_LENGTH bytes.",
                )
            }

            // Resolve destination
            val contactKey =
                resolveContactKey(recipientName, channelName)
                    ?: return@withTimeout SendMessageResult.InvalidArgument("Could not resolve destination.")

            // Handle ambiguous results from resolution
            if (contactKey is ResolvedContact.Ambiguous) {
                return@withTimeout SendMessageResult.AmbiguousName(contactKey.candidates)
            }

            val key = (contactKey as ResolvedContact.Resolved).contactKey

            // Send via existing use case and capture the generated messageId
            try {
                val messageId = sendMessageUseCase.invoke(text, key)

                SendMessageResult.Success(
                    messageId = messageId,
                    channel = contactKey.channelName,
                    timestamp = clock.now().toEpochMilliseconds(),
                )
            } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
                if (ex is CancellationException) throw ex
                SendMessageResult.InvalidArgument("Failed to send message: ${ex.message}")
            }
        }

    override suspend fun getMeshStatus(): MeshStatusResult = withTimeout(OPERATION_TIMEOUT) {
        val connectionState = serviceRepository.connectionState.value
        val onlineCount = nodeRepository.onlineNodeCount.first()
        val totalCount = nodeRepository.totalNodeCount.first()
        val ourNode = nodeRepository.ourNodeInfo.value
        val batteryLevel = ourNode?.batteryLevel?.takeIf { it in 1..MAX_BATTERY_LEVEL }
        val nodeName = ourNode?.user?.long_name?.takeIf { it.isNotBlank() }

        MeshStatusResult(
            connectionState = connectionState.name,
            onlineNodeCount = onlineCount,
            totalNodeCount = totalCount,
            localBatteryLevel = batteryLevel,
            localNodeName = nodeName,
        )
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun getNodeList(): GetNodeListResult = withTimeout(OPERATION_TIMEOUT) {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            return@withTimeout GetNodeListResult.NotConnected("Not connected to a Meshtastic radio.")
        }

        try {
            val nodeMap = nodeRepository.nodeDBbyNum.first()
            val nodes =
                nodeMap.values.map { node ->
                    NodeSummary(
                        id = NodeAddress.numToDefaultId(node.num),
                        name = node.user.long_name.takeIf { it.isNotBlank() } ?: "Node ${node.num}",
                        batteryLevel = node.deviceMetrics.battery_level?.coerceIn(0, MAX_BATTERY_LEVEL),
                        lastHeard = node.lastHeard.toLong() * MS_PER_SEC,
                        isOnline = node.isOnline,
                    )
                }
            GetNodeListResult.Success(nodes.sortedByDescending { it.lastHeard })
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            GetNodeListResult.Error("Failed to retrieve node list: ${ex.message}")
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun getChannelInfo(): GetChannelInfoResult = withTimeout(OPERATION_TIMEOUT) {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            return@withTimeout GetChannelInfoResult.NotConnected("Not connected to a Meshtastic radio.")
        }

        try {
            val channelSet = radioConfigRepository.channelSetFlow.first()
            val channels =
                channelSet.settings.mapIndexed { index, channel ->
                    ChannelSummary(
                        index = index,
                        name = channel.name.takeIf { it.isNotBlank() } ?: "Channel $index",
                        isPrimary = index == 0,
                        uplinkEnabled = channel.uplink_enabled,
                        downlinkEnabled = channel.downlink_enabled,
                    )
                }
            GetChannelInfoResult.Success(channels)
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            GetChannelInfoResult.Error("Failed to retrieve channel info: ${ex.message}")
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun getDeviceStatus(): GetDeviceStatusResult = withTimeout(OPERATION_TIMEOUT) {
        try {
            val ourNode =
                nodeRepository.ourNodeInfo.value
                    ?: return@withTimeout GetDeviceStatusResult.NotAvailable("Device not yet initialized.")

            val deviceStatus =
                DeviceStatus(
                    model = ourNode.metadata?.hw_model?.name ?: "Unknown",
                    firmwareVersion = ourNode.metadata?.firmware_version ?: "Unknown",
                    batteryLevel = ourNode.deviceMetrics.battery_level?.coerceIn(0, MAX_BATTERY_LEVEL),
                    chargingStatus = "UNKNOWN",
                    deviceName = ourNode.user.long_name.takeIf { it.isNotBlank() },
                    isActive = serviceRepository.connectionState.value == ConnectionState.Connected,
                )
            GetDeviceStatusResult.Success(deviceStatus)
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            GetDeviceStatusResult.Error("Failed to retrieve device status: ${ex.message}")
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun getNodeDetails(nodeId: String): GetNodeDetailsResult = withTimeout(OPERATION_TIMEOUT) {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            return@withTimeout GetNodeDetailsResult.NotConnected("Not connected to a Meshtastic radio.")
        }

        try {
            val node =
                if (nodeId.startsWith("!")) {
                    // Canonical hex node ID (e.g. "!ffffffff"). idToNum parses the full unsigned 32-bit
                    // range and returns null for malformed input, which we surface as NotFound.
                    val nodeNum =
                        NodeAddress.idToNum(nodeId)
                            ?: return@withTimeout GetNodeDetailsResult.NotFound("Node not found: $nodeId")
                    nodeRepository.nodeDBbyNum.first()[nodeNum]
                } else {
                    // User ID format
                    nodeRepository.getNode(nodeId)
                }

            if (node == null) {
                return@withTimeout GetNodeDetailsResult.NotFound("Node not found: $nodeId")
            }

            // Check if position is valid (both coords zero AND time zero indicates no position fix)
            val hasValidPosition = node.latitude != 0.0 || node.longitude != 0.0 || node.position.time > 0

            val details =
                NodeDetails(
                    id = NodeAddress.numToDefaultId(node.num),
                    userId = node.user.id,
                    name = node.user.long_name.takeIf { it.isNotBlank() } ?: "Node ${node.num}",
                    batteryLevel = node.deviceMetrics.battery_level?.coerceIn(0, MAX_BATTERY_LEVEL),
                    voltage = node.deviceMetrics.voltage,
                    hardwareModel = node.metadata?.hw_model?.name ?: "Unknown",
                    firmwareVersion = node.metadata?.firmware_version ?: "Unknown",
                    snr = node.snr,
                    rssi = node.rssi,
                    hopsAway = node.hopsAway,
                    channel = node.channel,
                    lastHeard = node.lastHeard.toLong() * MS_PER_SEC,
                    userRole = node.user.role.name,
                    isLicensed = node.user.is_licensed,
                    latitude = node.latitude.takeIf { hasValidPosition },
                    longitude = node.longitude.takeIf { hasValidPosition },
                )
            GetNodeDetailsResult.Success(details)
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            GetNodeDetailsResult.Error("Failed to retrieve node details: ${ex.message}")
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun getMeshMetrics(): GetMeshMetricsResult = withTimeout(OPERATION_TIMEOUT) {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            return@withTimeout GetMeshMetricsResult.NotConnected("Not connected to a Meshtastic radio.")
        }

        try {
            val totalCount = nodeRepository.totalNodeCount.first()
            val onlineCount = nodeRepository.onlineNodeCount.first()

            // Calculate average battery level
            val nodeMap = nodeRepository.nodeDBbyNum.first()
            val batteryLevels = nodeMap.values.mapNotNull { it.deviceMetrics.battery_level }
            val avgBattery =
                if (batteryLevels.isNotEmpty()) {
                    (batteryLevels.sum() / batteryLevels.size).coerceIn(0, MAX_BATTERY_LEVEL)
                } else {
                    null
                }

            // Mesh health score: 0-100 based on online ratio and recent activity
            val healthScore =
                when {
                    totalCount == 0 -> 0
                    onlineCount == 0 -> HEALTH_SCORE_DEGRADED
                    else -> HEALTH_SCORE_BASE + (HEALTH_SCORE_ONLINE_RATIO * onlineCount) / totalCount
                }

            // Find most recent packet: max lastHeard across all nodes (convert seconds to ms)
            val mostRecentPacketTimeMs =
                nodeMap.values.maxOfOrNull { it.lastHeard }?.takeIf { it > 0 }?.toLong()?.times(MS_PER_SEC)
                    ?: clock.now().toEpochMilliseconds()

            // Get local device uptime from its DeviceMetrics (node #0 is typically the local device)
            val localNode = nodeMap.values.find { it.num == 0 } ?: nodeMap.values.firstOrNull()
            val meshUptimeSeconds = localNode?.deviceMetrics?.uptime_seconds?.toLong() ?: 0L

            val metrics =
                MeshMetrics(
                    totalNodeCount = totalCount,
                    onlineNodeCount = onlineCount,
                    averageBatteryLevel = avgBattery,
                    meshHealthScore = healthScore.coerceIn(0, HEALTH_SCORE_MAX),
                    mostRecentPacketTime = mostRecentPacketTimeMs,
                    meshUptimeSeconds = meshUptimeSeconds,
                    channelUtilizationPercent = null, // Could compute from radioConfigRepository if needed
                )
            GetMeshMetricsResult.Success(metrics)
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            GetMeshMetricsResult.Error("Failed to retrieve mesh metrics: ${ex.message}")
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun getRecentMessages(contactName: String?, limit: Int): GetRecentMessagesResult =
        withTimeout(OPERATION_TIMEOUT) {
            try {
                val effectiveLimit = limit.coerceIn(1, AiFunctionProvider.MAX_MESSAGE_LIMIT)

                // Resolve contact key if a name filter is provided
                val contactKey =
                    if (contactName != null) {
                        resolveContactKeyForRead(contactName)
                            ?: return@withTimeout GetRecentMessagesResult.ContactNotFound(
                                "Contact not found: $contactName",
                            )
                    } else {
                        null
                    }

                val messages =
                    if (contactKey != null) {
                        // Fetch messages from a specific contact
                        packetRepository
                            .getMessagesFrom(
                                contact = contactKey,
                                limit = effectiveLimit,
                                includeFiltered = false,
                                getNode = { userId -> nodeRepository.getNode(userId ?: "") },
                            )
                            .first()
                    } else {
                        // Fetch recent messages across all contacts
                        val contacts = packetRepository.getContacts().first()
                        contacts.keys
                            .flatMap { key ->
                                packetRepository
                                    .getMessagesFrom(
                                        contact = key,
                                        limit = MESSAGES_PER_CONTACT,
                                        includeFiltered = false,
                                        getNode = { userId -> nodeRepository.getNode(userId ?: "") },
                                    )
                                    .first()
                            }
                            .sortedByDescending { it.receivedTime }
                            .take(effectiveLimit)
                    }

                val channelSet = radioConfigRepository.channelSetFlow.first()
                val summaries =
                    messages.map { msg ->
                        MessageSummary(
                            senderName = msg.node.user.long_name.takeIf { it.isNotBlank() } ?: "Node ${msg.node.num}",
                            text = msg.text,
                            contactName = resolveContactDisplayName(msg, channelSet),
                            receivedTime = msg.receivedTime,
                            fromLocal = msg.fromLocal,
                            read = msg.read,
                        )
                    }

                GetRecentMessagesResult.Success(summaries)
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                GetRecentMessagesResult.Error("Failed to retrieve messages: ${ex.message}")
            }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getUnreadSummary(): GetUnreadSummaryResult = withTimeout(OPERATION_TIMEOUT) {
        try {
            val contacts = packetRepository.getContacts().first()
            val settings = packetRepository.getContactSettings().first()
            val channelSet = radioConfigRepository.channelSetFlow.first()
            val nodeMap = nodeRepository.nodeDBbyNum.first()

            val nonMutedContacts = contacts.filter { (key, _) -> settings[key]?.isMuted != true }

            val contactUnreads =
                nonMutedContacts.mapNotNull { (contactKey, lastPacket) ->
                    val unreadCount = packetRepository.getUnreadCount(contactKey)
                    if (unreadCount <= 0) return@mapNotNull null

                    val isBroadcast = lastPacket.to == NodeAddress.ID_BROADCAST
                    val displayName =
                        if (isBroadcast) {
                            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: 0
                            channelSet.settings.getOrNull(channelIndex)?.name?.ifBlank { "Channel $channelIndex" }
                                ?: "Channel $channelIndex"
                        } else {
                            val userId = lastPacket.from ?: ""
                            val node = nodeMap.values.find { it.user.id == userId }
                            node?.user?.long_name?.takeIf { it.isNotBlank() } ?: "Unknown"
                        }

                    ContactUnread(
                        name = displayName,
                        unreadCount = unreadCount,
                        lastMessagePreview = lastPacket.text?.take(MESSAGE_PREVIEW_MAX_LENGTH),
                        lastMessageTime = lastPacket.time.takeIf { it > 0 },
                    )
                }

            val totalUnread = contactUnreads.sumOf { it.unreadCount }

            GetUnreadSummaryResult.Success(
                UnreadSummary(
                    totalUnreadCount = totalUnread,
                    contacts = contactUnreads.sortedByDescending { it.lastMessageTime },
                ),
            )
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            GetUnreadSummaryResult.Error("Failed to retrieve unread summary: ${ex.message}")
        }
    }

    /**
     * Resolve a contact name (node or channel) to a contact key for reading messages. Returns null if the name cannot
     * be resolved.
     */
    @Suppress("ReturnCount")
    private suspend fun resolveContactKeyForRead(name: String): String? {
        // Try node name first
        when (val nodeResult = fuzzyNameResolver.resolveNodeName(name)) {
            is NodeNameResult.Found -> {
                val channelIndex = NodeAddress.PKC_CHANNEL_INDEX
                return "${channelIndex}${nodeResult.userId}"
            }

            is NodeNameResult.Ambiguous -> return null

            is NodeNameResult.NotFound -> {
                /* fall through to channel */
            }
        }

        // Try channel name
        return when (val channelResult = fuzzyNameResolver.resolveChannelName(name)) {
            is ChannelNameResult.Found -> "${channelResult.channelIndex}${NodeAddress.ID_BROADCAST}"
            is ChannelNameResult.Ambiguous -> null
            is ChannelNameResult.NotFound -> null
        }
    }

    private fun resolveContactDisplayName(
        msg: org.meshtastic.core.model.Message,
        channelSet: org.meshtastic.proto.ChannelSet,
    ): String {
        // For broadcast messages, use channel name
        val channelIndex = msg.node.channel
        return channelSet.settings.getOrNull(channelIndex)?.name?.ifBlank { "Channel $channelIndex" }
            ?: "Channel $channelIndex"
    }

    @Suppress("ReturnCount")
    private suspend fun resolveContactKey(recipientName: String?, channelName: String?): ResolvedContact? {
        // Direct message to a specific node
        if (recipientName != null) {
            return when (val result = fuzzyNameResolver.resolveNodeName(recipientName)) {
                is NodeNameResult.Found -> {
                    // DM contact key format: channel_index + nodeId
                    // For PKC DMs, use channel index 8; for legacy use no channel prefix
                    val channelIndex = NodeAddress.PKC_CHANNEL_INDEX
                    ResolvedContact.Resolved(
                        contactKey = "${channelIndex}${result.userId}",
                        channelName = "DM to $recipientName",
                    )
                }

                is NodeNameResult.Ambiguous -> ResolvedContact.Ambiguous(result.candidates)

                is NodeNameResult.NotFound -> {
                    return null
                }
            }
        }

        // Broadcast to a specific channel
        if (channelName != null) {
            return when (val result = fuzzyNameResolver.resolveChannelName(channelName)) {
                is ChannelNameResult.Found ->
                    ResolvedContact.Resolved(
                        contactKey = "${result.channelIndex}${NodeAddress.ID_BROADCAST}",
                        channelName = result.name,
                    )

                is ChannelNameResult.Ambiguous -> ResolvedContact.Ambiguous(result.candidates)

                is ChannelNameResult.NotFound -> null
            }
        }

        // Default: broadcast on primary channel (index 0)
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val primaryName = channelSet.settings.firstOrNull()?.name?.ifBlank { "Primary" } ?: "Primary"
        return ResolvedContact.Resolved(contactKey = "0${NodeAddress.ID_BROADCAST}", channelName = primaryName)
    }

    private sealed class ResolvedContact {
        data class Resolved(val contactKey: String, val channelName: String) : ResolvedContact()

        data class Ambiguous(val candidates: List<String>) : ResolvedContact()
    }

    companion object {
        private val OPERATION_TIMEOUT = 5.seconds
        private const val MAX_BATTERY_LEVEL = 100
        private const val MS_PER_SEC = 1000L
        private const val HEALTH_SCORE_BASE = 50
        private const val HEALTH_SCORE_ONLINE_RATIO = 50
        private const val HEALTH_SCORE_DEGRADED = 10
        private const val HEALTH_SCORE_MAX = 100
        private const val MESSAGES_PER_CONTACT = 5
        private const val MESSAGE_PREVIEW_MAX_LENGTH = 100

        /** Standard Meshtastic message payload limit (bytes). */
        const val MAX_MESSAGE_LENGTH = 237
    }
}

/** Extension to get a display name for ConnectionState. */
private val ConnectionState.name: String
    get() =
        when (this) {
            ConnectionState.Connected -> "CONNECTED"
            ConnectionState.Connecting -> "CONNECTING"
            ConnectionState.Disconnected -> "DISCONNECTED"
            ConnectionState.DeviceSleep -> "DEVICE_SLEEP"
        }
