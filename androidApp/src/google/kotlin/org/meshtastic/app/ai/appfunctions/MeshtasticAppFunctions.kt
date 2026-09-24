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
package org.meshtastic.app.ai.appfunctions

import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionNotSupportedException
import kotlinx.coroutines.TimeoutCancellationException
import org.meshtastic.core.data.ai.AiFunctionProvider
import org.meshtastic.core.data.ai.SendMessageResult

/**
 * Maps [AiFunctionProvider] results to App Functions responses and exceptions. [BaseMeshtasticAppFunctionService]
 * declares the functions agents see and delegates each one here.
 */
class MeshtasticAppFunctions(private val provider: AiFunctionProvider) {

    suspend fun sendMessage(text: String, recipientName: String?, channelName: String?): SendMessageResponse {
        val result =
            try {
                provider.sendMessage(text, recipientName, channelName)
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }

        return when (result) {
            is SendMessageResult.Success ->
                SendMessageResponse(
                    messageId = result.messageId,
                    channel = result.channel,
                    timestamp = result.timestamp,
                )

            is SendMessageResult.NotConnected -> throw AppFunctionNotSupportedException(result.message)

            is SendMessageResult.AmbiguousName -> {
                val names = result.candidates.joinToString()
                throw AppFunctionInvalidArgumentException(
                    "Multiple nodes match that name: $names. Please be more specific.",
                )
            }

            is SendMessageResult.InvalidArgument -> throw AppFunctionInvalidArgumentException(result.reason)

            is SendMessageResult.RateLimited ->
                throw AppFunctionInvalidArgumentException(
                    "Rate limit exceeded. Try again in ${result.retryAfterSeconds} seconds.",
                )
        }
    }

    suspend fun getMeshStatus(): MeshStatusResponse {
        val status =
            try {
                provider.getMeshStatus()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }

        return MeshStatusResponse(
            connectionState = status.connectionState,
            onlineNodeCount = status.onlineNodeCount,
            totalNodeCount = status.totalNodeCount,
            localBatteryLevel = status.localBatteryLevel,
            localNodeName = status.localNodeName,
        )
    }

    suspend fun getNodeList(): GetNodeListResponse {
        val result =
            try {
                provider.getNodeList()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }

        return when (result) {
            is org.meshtastic.core.data.ai.GetNodeListResult.Success ->
                GetNodeListResponse(
                    nodes =
                    result.nodes.map {
                        NodeInfo(
                            id = it.id,
                            name = it.name,
                            batteryLevel = it.batteryLevel,
                            lastHeard = it.lastHeard,
                            isOnline = it.isOnline,
                        )
                    },
                )

            is org.meshtastic.core.data.ai.GetNodeListResult.NotConnected ->
                throw AppFunctionNotSupportedException(result.message)

            is org.meshtastic.core.data.ai.GetNodeListResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }

    suspend fun getChannelInfo(): GetChannelInfoResponse {
        val result =
            try {
                provider.getChannelInfo()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }

        return when (result) {
            is org.meshtastic.core.data.ai.GetChannelInfoResult.Success ->
                GetChannelInfoResponse(
                    channels =
                    result.channels.map {
                        ChannelInfo(
                            index = it.index,
                            name = it.name,
                            isPrimary = it.isPrimary,
                            uplinkEnabled = it.uplinkEnabled,
                            downlinkEnabled = it.downlinkEnabled,
                        )
                    },
                )

            is org.meshtastic.core.data.ai.GetChannelInfoResult.NotConnected ->
                throw AppFunctionNotSupportedException(result.message)

            is org.meshtastic.core.data.ai.GetChannelInfoResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }

    suspend fun getDeviceStatus(): GetDeviceStatusResponse {
        val result =
            try {
                provider.getDeviceStatus()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the device is initialized and try again.",
                )
            }

        return when (result) {
            is org.meshtastic.core.data.ai.GetDeviceStatusResult.Success ->
                GetDeviceStatusResponse(
                    model = result.device.model,
                    firmwareVersion = result.device.firmwareVersion,
                    batteryLevel = result.device.batteryLevel,
                    chargingStatus = result.device.chargingStatus,
                    deviceName = result.device.deviceName,
                    isActive = result.device.isActive,
                )

            is org.meshtastic.core.data.ai.GetDeviceStatusResult.NotAvailable ->
                throw AppFunctionNotSupportedException(result.message)

            is org.meshtastic.core.data.ai.GetDeviceStatusResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }

    suspend fun getNodeDetails(nodeId: String): GetNodeDetailsResponse {
        val result =
            try {
                provider.getNodeDetails(nodeId)
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }
        return when (result) {
            is org.meshtastic.core.data.ai.GetNodeDetailsResult.Success ->
                GetNodeDetailsResponse(
                    id = result.node.id,
                    userId = result.node.userId,
                    name = result.node.name,
                    batteryLevel = result.node.batteryLevel,
                    voltage = result.node.voltage,
                    hardwareModel = result.node.hardwareModel,
                    firmwareVersion = result.node.firmwareVersion,
                    snr = result.node.snr,
                    rssi = result.node.rssi,
                    hopsAway = result.node.hopsAway,
                    channel = result.node.channel,
                    lastHeard = result.node.lastHeard,
                    userRole = result.node.userRole,
                    isLicensed = result.node.isLicensed,
                    latitude = result.node.latitude,
                    longitude = result.node.longitude,
                )

            is org.meshtastic.core.data.ai.GetNodeDetailsResult.NotConnected ->
                throw AppFunctionNotSupportedException(result.message)

            is org.meshtastic.core.data.ai.GetNodeDetailsResult.NotFound ->
                throw AppFunctionElementNotFoundException(result.message)

            is org.meshtastic.core.data.ai.GetNodeDetailsResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }

    suspend fun getMeshMetrics(): GetMeshMetricsResponse {
        val result =
            try {
                provider.getMeshMetrics()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }
        return when (result) {
            is org.meshtastic.core.data.ai.GetMeshMetricsResult.Success ->
                GetMeshMetricsResponse(
                    totalNodeCount = result.metrics.totalNodeCount,
                    onlineNodeCount = result.metrics.onlineNodeCount,
                    averageBatteryLevel = result.metrics.averageBatteryLevel,
                    meshHealthScore = result.metrics.meshHealthScore,
                    mostRecentPacketTime = result.metrics.mostRecentPacketTime,
                    meshUptimeSeconds = result.metrics.meshUptimeSeconds,
                    channelUtilizationPercent = result.metrics.channelUtilizationPercent,
                )

            is org.meshtastic.core.data.ai.GetMeshMetricsResult.NotConnected ->
                throw AppFunctionNotSupportedException(result.message)

            is org.meshtastic.core.data.ai.GetMeshMetricsResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }

    suspend fun getRecentMessages(contactName: String?, limit: Int): GetRecentMessagesResponse {
        val result =
            try {
                provider.getRecentMessages(contactName, limit)
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException("Request timed out. Try again or reduce the message limit.")
            }
        return when (result) {
            is org.meshtastic.core.data.ai.GetRecentMessagesResult.Success ->
                GetRecentMessagesResponse(
                    messages =
                    result.messages.map { msg ->
                        MessageInfo(
                            senderName = msg.senderName,
                            text = msg.text,
                            contactName = msg.contactName,
                            receivedTime = msg.receivedTime,
                            fromLocal = msg.fromLocal,
                            read = msg.read,
                        )
                    },
                )

            is org.meshtastic.core.data.ai.GetRecentMessagesResult.ContactNotFound ->
                throw AppFunctionElementNotFoundException(result.message)

            is org.meshtastic.core.data.ai.GetRecentMessagesResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }

    suspend fun getUnreadSummary(): GetUnreadSummaryResponse {
        val result =
            try {
                provider.getUnreadSummary()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException("Request timed out. Try again.")
            }
        return when (result) {
            is org.meshtastic.core.data.ai.GetUnreadSummaryResult.Success ->
                GetUnreadSummaryResponse(
                    totalUnreadCount = result.summary.totalUnreadCount,
                    contacts =
                    result.summary.contacts.map { contact ->
                        ContactUnreadInfo(
                            name = contact.name,
                            unreadCount = contact.unreadCount,
                            lastMessagePreview = contact.lastMessagePreview,
                            lastMessageTime = contact.lastMessageTime,
                        )
                    },
                )

            is org.meshtastic.core.data.ai.GetUnreadSummaryResult.Error ->
                throw AppFunctionInvalidArgumentException(result.reason)
        }
    }
}
