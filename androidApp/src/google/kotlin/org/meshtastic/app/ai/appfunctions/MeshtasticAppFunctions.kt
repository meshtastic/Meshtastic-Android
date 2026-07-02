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

import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionIntValueConstraint
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionNotSupportedException
import kotlinx.coroutines.TimeoutCancellationException
import org.meshtastic.core.data.ai.AiFunctionProvider
import org.meshtastic.core.data.ai.SendMessageResult

/**
 * Exposes Meshtastic mesh networking capabilities to system AI assistants via the Android App Functions API. Functions
 * declared here are discoverable by the system and can be invoked by AI agents such as Gemini.
 */
class MeshtasticAppFunctions(private val provider: AiFunctionProvider) {

    /**
     * Send a text message over the Meshtastic mesh radio network.
     *
     * Messages are transmitted to nearby mesh nodes using LoRa radio. The mesh network is ideal for off-grid
     * communications where cellular service is unavailable.
     *
     * @param context The app function invocation context provided by the system.
     * @param text The message text to send (max 237 bytes).
     * @param recipientName Optional name of a specific node to send a direct message to. If omitted, the message is
     *   broadcast to all nodes on the specified channel.
     * @param channelName Optional channel name to broadcast on. If omitted, uses the primary channel. Ignored when
     *   recipientName is specified.
     * @return A [SendMessageResponse] with the message ID, channel, and timestamp.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendMessage(
        context: AppFunctionContext,
        text: String,
        recipientName: String? = null,
        channelName: String? = null,
    ): SendMessageResponse {
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

    /**
     * Get the current status of the Meshtastic mesh network.
     *
     * Returns connection state, number of online nodes, total known nodes, the connected device's battery level, and
     * the local node name.
     *
     * @param context The app function invocation context provided by the system.
     * @return A [MeshStatusResponse] with the current mesh network status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMeshStatus(context: AppFunctionContext): MeshStatusResponse {
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

    /**
     * List all nodes currently visible on the Meshtastic mesh network.
     *
     * Returns detailed information about each node including name, battery level, and last heard time. Nodes are sorted
     * by most recently heard first.
     *
     * @param context The app function invocation context provided by the system.
     * @return A list of nodes with their current status and metrics.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNodeList(context: AppFunctionContext): GetNodeListResponse {
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

    /**
     * List all available Meshtastic mesh channels and their configurations.
     *
     * Returns details about each channel including name, index, primary status, and uplink/downlink settings.
     *
     * @param context The app function invocation context provided by the system.
     * @return A list of channels with their current configuration.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getChannelInfo(context: AppFunctionContext): GetChannelInfoResponse {
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

    /**
     * Get the status and metrics of the local Meshtastic radio device.
     *
     * Returns hardware model, firmware version, battery level, charging status, and current radio state.
     *
     * @param context The app function invocation context provided by the system.
     * @return Device status with current metrics and configuration.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceStatus(context: AppFunctionContext): GetDeviceStatusResponse {
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

    /**
     * Retrieve detailed telemetry and status for a specific mesh node.
     *
     * Returns per-node metrics including battery level, signal strength, hardware model, and location data.
     *
     * @param context The app function invocation context provided by the system.
     * @param nodeId The target node ID (e.g., '!abc12345' or user ID).
     * @return A [GetNodeDetailsResponse] with detailed node information.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNodeDetails(context: AppFunctionContext, nodeId: String): GetNodeDetailsResponse {
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

    /**
     * Retrieve aggregate network metrics and statistics for the entire mesh.
     *
     * Returns mesh-wide analytics including total node count, online nodes, average battery level, and health score.
     *
     * @param context The app function invocation context provided by the system.
     * @return A [GetMeshMetricsResponse] with mesh-wide statistics.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMeshMetrics(context: AppFunctionContext): GetMeshMetricsResponse {
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

    /**
     * Retrieve recent messages received over the Meshtastic mesh radio network.
     *
     * Returns a list of recent messages from the local message history. Messages are stored locally and do not require
     * an active mesh connection. Useful for catching up on conversations or reviewing recent communications.
     *
     * @param context The app function invocation context provided by the system.
     * @param contactName Optional name of a node or channel to filter messages from. If omitted, returns messages from
     *   all contacts sorted by most recent.
     * @param limit Maximum number of messages to return (1–50). Defaults to 20.
     * @return A [GetRecentMessagesResponse] containing the list of recent messages.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentMessages(
        context: AppFunctionContext,
        contactName: String? = null,
        @AppFunctionIntValueConstraint(enumValues = [1, 5, 10, 20, 50])
        limit: Int = AiFunctionProvider.DEFAULT_MESSAGE_LIMIT,
    ): GetRecentMessagesResponse {
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

    /**
     * Get a summary of unread messages across all Meshtastic mesh contacts.
     *
     * Returns the total unread count and a per-contact breakdown showing who sent unread messages, how many are unread,
     * and a preview of the last message. Muted contacts are excluded. Does not require an active mesh connection.
     *
     * @param context The app function invocation context provided by the system.
     * @return A [GetUnreadSummaryResponse] with the total unread count and per-contact details.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getUnreadSummary(context: AppFunctionContext): GetUnreadSummaryResponse {
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
