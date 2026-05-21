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
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of [AiFunctionProvider] that bridges AI function invocations to existing Meshtastic repositories and
 * use cases.
 */
@Single(binds = [AiFunctionProvider::class])
class AiFunctionProviderImpl(
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
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
            val messageId = sendMessageUseCase.invoke(text, key)

            SendMessageResult.Success(
                messageId = messageId,
                channel = contactKey.channelName,
                timestamp = clock.now().toEpochMilliseconds(),
            )
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
                    val elapsedTimeMs = clock.now().toEpochMilliseconds() - node.lastHeard.toLong() * MS_PER_SEC
                    NodeSummary(
                        id = "!${node.num.toString(HEX_RADIX)}",
                        name = node.user.long_name.takeIf { it.isNotBlank() } ?: "Node ${node.num}",
                        batteryLevel = node.deviceMetrics.battery_level?.coerceIn(0, MAX_BATTERY_LEVEL),
                        lastHeard = node.lastHeard.toLong() * MS_PER_SEC,
                        isOnline = elapsedTimeMs < ONLINE_THRESHOLD_MS,
                    )
                }
            GetNodeListResult.Success(nodes.sortedByDescending { it.lastHeard })
        } catch (ex: Exception) {
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
            GetDeviceStatusResult.Error("Failed to retrieve device status: ${ex.message}")
        }
    }

    @Suppress("ReturnCount")
    private suspend fun resolveContactKey(recipientName: String?, channelName: String?): ResolvedContact? {
        // Direct message to a specific node
        if (recipientName != null) {
            return when (val result = fuzzyNameResolver.resolveNodeName(recipientName)) {
                is NodeNameResult.Found -> {
                    // DM contact key format: channel_index + nodeId
                    // For PKC DMs, use channel index 8; for legacy use no channel prefix
                    val channelIndex = DataPacket.PKC_CHANNEL_INDEX
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
                        contactKey = "${result.channelIndex}${DataPacket.ID_BROADCAST}",
                        channelName = result.name,
                    )

                is ChannelNameResult.Ambiguous -> ResolvedContact.Ambiguous(result.candidates)

                is ChannelNameResult.NotFound -> null
            }
        }

        // Default: broadcast on primary channel (index 0)
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val primaryName = channelSet.settings.firstOrNull()?.name?.ifBlank { "Primary" } ?: "Primary"
        return ResolvedContact.Resolved(contactKey = "0${DataPacket.ID_BROADCAST}", channelName = primaryName)
    }

    private sealed class ResolvedContact {
        data class Resolved(val contactKey: String, val channelName: String) : ResolvedContact()

        data class Ambiguous(val candidates: List<String>) : ResolvedContact()
    }

    companion object {
        private val OPERATION_TIMEOUT = 5.seconds
        private const val MAX_BATTERY_LEVEL = 100
        private const val ONLINE_THRESHOLD_MS = 30_000L
        private const val HEX_RADIX = 16
        private const val MS_PER_SEC = 1000L

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
