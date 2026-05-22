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

import androidx.appfunctions.AppFunctionSerializable

/** Response returned when a message is successfully sent via the mesh network. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SendMessageResponse(
    /** The identifier assigned to the outgoing message. */
    val messageId: Int,
    /** The channel or destination the message was sent to. */
    val channel: String,
    /** The time the message was sent (epoch milliseconds). */
    val timestamp: Long,
)

/** Response containing the current status of the Meshtastic mesh network. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MeshStatusResponse(
    /** The current radio connection state (e.g., CONNECTED, DISCONNECTED). */
    val connectionState: String,
    /** The number of nodes currently online (heard within the last 2 hours). */
    val onlineNodeCount: Int,
    /** The total number of nodes known to the network. */
    val totalNodeCount: Int,
    /** The battery percentage of the connected Meshtastic device (1-100), or null if unavailable. */
    val localBatteryLevel: Int?,
    /** The display name of the local node, or null if not set. */
    val localNodeName: String?,
)

/** Information about a single mesh node. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class NodeInfo(
    /** The unique node identifier in Meshtastic hex format (e.g., !abc12345). */
    val id: String,
    /** The human-readable name of the node. */
    val name: String,
    /** The node's battery percentage (0-100), or null if unavailable. */
    val batteryLevel: Int?,
    /** The time this node was last heard from (epoch milliseconds). */
    val lastHeard: Long,
    /** Whether this node is currently considered online. */
    val isOnline: Boolean,
)

/** Response containing a list of nodes visible on the mesh network. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetNodeListResponse(
    /** List of nodes sorted by most recently heard first. */
    val nodes: List<NodeInfo>,
)

/** Information about a single mesh channel. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChannelInfo(
    /** The channel index (0-7). */
    val index: Int,
    /** The human-readable name of the channel. */
    val name: String,
    /** Whether this is the primary/default channel. */
    val isPrimary: Boolean,
    /** Whether uplink is enabled for this channel. */
    val uplinkEnabled: Boolean,
    /** Whether downlink is enabled for this channel. */
    val downlinkEnabled: Boolean,
)

/** Response containing the list of available mesh channels. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetChannelInfoResponse(
    /** List of all configured channels. */
    val channels: List<ChannelInfo>,
)

/** Response containing the status of the local Meshtastic device. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetDeviceStatusResponse(
    /** The hardware model of the device (e.g., "Meshtastic nRF52840"). */
    val model: String,
    /** The firmware version string. */
    val firmwareVersion: String,
    /** The device battery percentage (0-100), or null if not battery-powered. */
    val batteryLevel: Int?,
    /** The charging state (CHARGING, NOT_CHARGING, or UNKNOWN). */
    val chargingStatus: String,
    /** The display name of the device, or null if not set. */
    val deviceName: String?,
    /** Whether the radio is currently active and connected. */
    val isActive: Boolean,
)

/** Response containing detailed telemetry for a specific mesh node. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetNodeDetailsResponse(
    /** Node ID in hex format (e.g., "!abc12345"). */
    val id: String,
    /** User ID string for this node. */
    val userId: String,
    /** Display name of the node. */
    val name: String,
    /** Battery percentage (0-100), or null if unavailable. */
    val batteryLevel: Int?,
    /** Supply voltage in volts, or null if unavailable. */
    val voltage: Float?,
    /** Hardware model string. */
    val hardwareModel: String,
    /** Firmware version string. */
    val firmwareVersion: String,
    /** Signal-to-noise ratio of strongest signal. */
    val snr: Float,
    /** Received signal strength indicator in dB. */
    val rssi: Int,
    /** Number of hops away from local node (-1 if unknown). */
    val hopsAway: Int,
    /** Channel index this node is on. */
    val channel: Int,
    /** Last heard timestamp (milliseconds since epoch). */
    val lastHeard: Long,
    /** User role or device type. */
    val userRole: String,
    /** Whether the user is licensed. */
    val isLicensed: Boolean,
    /** Latitude in degrees, or null if unknown. */
    val latitude: Double?,
    /** Longitude in degrees, or null if unknown. */
    val longitude: Double?,
)

/** Response containing aggregate mesh network metrics. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetMeshMetricsResponse(
    /** Total number of known nodes. */
    val totalNodeCount: Int,
    /** Number of nodes currently online. */
    val onlineNodeCount: Int,
    /** Average battery level across mesh, or null if no data. */
    val averageBatteryLevel: Int?,
    /** Estimated health score (0-100). */
    val meshHealthScore: Int,
    /** Timestamp of most recent packet (ms since epoch). */
    val mostRecentPacketTime: Long,
    /** Mesh uptime in seconds. */
    val meshUptimeSeconds: Long,
    /** Channel utilization percentage, or null if unavailable. */
    val channelUtilizationPercent: Int?,
)

/** Response containing recent messages from the mesh network. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetRecentMessagesResponse(
    /** List of recent messages ordered by most recent first. */
    val messages: List<MessageInfo>,
)

/** Information about a single mesh message. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MessageInfo(
    /** Display name of the message sender. */
    val senderName: String,
    /** The message text content. */
    val text: String,
    /** Name of the channel or contact the message belongs to. */
    val contactName: String,
    /** Timestamp when the message was received (ms since epoch). */
    val receivedTime: Long,
    /** True if this message was sent by the local user. */
    val fromLocal: Boolean,
    /** True if this message has been read by the user. */
    val read: Boolean,
)

/** Response containing a summary of unread messages across all contacts. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetUnreadSummaryResponse(
    /** Total number of unread messages across all non-muted contacts. */
    val totalUnreadCount: Int,
    /** Per-contact breakdown of unread messages, sorted by most recent. */
    val contacts: List<ContactUnreadInfo>,
)

/** Unread message details for a single contact or channel. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ContactUnreadInfo(
    /** Display name of the contact or channel. */
    val name: String,
    /** Number of unread messages from this contact. */
    val unreadCount: Int,
    /** Preview text of the most recent message (up to 100 chars), or null if unavailable. */
    val lastMessagePreview: String?,
    /** Timestamp of the most recent message (ms since epoch), or null if unavailable. */
    val lastMessageTime: Long?,
)
