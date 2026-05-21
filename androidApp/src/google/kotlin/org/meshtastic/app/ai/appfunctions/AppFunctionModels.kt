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

/**
 * Response returned when a message is successfully sent via the mesh network.
 *
 * @property messageId The identifier assigned to the outgoing message.
 * @property channel The channel or destination the message was sent to.
 * @property timestamp The time the message was sent (epoch milliseconds).
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SendMessageResponse(val messageId: Int, val channel: String, val timestamp: Long)

/**
 * Response containing the current status of the Meshtastic mesh network.
 *
 * @property connectionState The current radio connection state (e.g., CONNECTED, DISCONNECTED).
 * @property onlineNodeCount The number of nodes currently online (heard within the last 15 minutes).
 * @property totalNodeCount The total number of nodes known to the network.
 * @property localBatteryLevel The battery percentage of the connected Meshtastic device (1-100), or null if
 *   unavailable.
 * @property localNodeName The display name of the local node, or null if not set.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MeshStatusResponse(
    val connectionState: String,
    val onlineNodeCount: Int,
    val totalNodeCount: Int,
    val localBatteryLevel: Int?,
    val localNodeName: String?,
)

/**
 * Response containing information about a single mesh node.
 *
 * @property id The unique node identifier in Meshtastic hex format (e.g., !abc12345).
 * @property name The human-readable name of the node.
 * @property batteryLevel The node's battery percentage (0-100), or null if unavailable.
 * @property lastHeard The time this node was last heard from (epoch milliseconds).
 * @property isOnline Whether this node is currently considered online.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class NodeInfo(
    val id: String,
    val name: String,
    val batteryLevel: Int?,
    val lastHeard: Long,
    val isOnline: Boolean,
)

/**
 * Response containing a list of nodes visible on the mesh network.
 *
 * @property nodes List of nodes sorted by most recently heard first.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetNodeListResponse(val nodes: List<NodeInfo>)

/**
 * Response containing information about a single mesh channel.
 *
 * @property index The channel index (0-7).
 * @property name The human-readable name of the channel.
 * @property isPrimary Whether this is the primary/default channel.
 * @property uplinkEnabled Whether uplink is enabled for this channel.
 * @property downlinkEnabled Whether downlink is enabled for this channel.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChannelInfo(
    val index: Int,
    val name: String,
    val isPrimary: Boolean,
    val uplinkEnabled: Boolean,
    val downlinkEnabled: Boolean,
)

/**
 * Response containing the list of available mesh channels.
 *
 * @property channels List of all configured channels.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetChannelInfoResponse(val channels: List<ChannelInfo>)

/**
 * Response containing the status of the local Meshtastic device.
 *
 * @property model The hardware model of the device (e.g., "Meshtastic nRF52840").
 * @property firmwareVersion The firmware version string.
 * @property batteryLevel The device battery percentage (0-100), or null if not battery-powered.
 * @property chargingStatus The charging state (CHARGING, NOT_CHARGING, or UNKNOWN).
 * @property deviceName The display name of the device, or null if not set.
 * @property isActive Whether the radio is currently active and connected.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GetDeviceStatusResponse(
    val model: String,
    val firmwareVersion: String,
    val batteryLevel: Int?,
    val chargingStatus: String,
    val deviceName: String?,
    val isActive: Boolean,
)
