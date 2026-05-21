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

/** Result of a [AiFunctionProvider.sendMessage] invocation. */
sealed class SendMessageResult {
    /** Message was successfully queued for transmission. */
    data class Success(val messageId: Int, val channel: String, val timestamp: Long) : SendMessageResult()

    /** Device is not connected to a Meshtastic radio. */
    data class NotConnected(val message: String) : SendMessageResult()

    /** The provided name matched multiple candidates. */
    data class AmbiguousName(val candidates: List<String>) : SendMessageResult()

    /** An argument was invalid (e.g., message too long, name not found). */
    data class InvalidArgument(val reason: String) : SendMessageResult()

    /** Rate limit exceeded — too many AI-triggered sends in the time window. */
    data class RateLimited(val retryAfterSeconds: Int) : SendMessageResult()
}

/** Result of a [AiFunctionProvider.getMeshStatus] invocation. */
data class MeshStatusResult(
    /** Current connection state (e.g., "CONNECTED", "DISCONNECTED"). */
    val connectionState: String,
    /** Number of nodes heard within the online threshold. */
    val onlineNodeCount: Int,
    /** Total number of nodes in the local database. */
    val totalNodeCount: Int,
    /** Local device battery level (0-100), or null if unavailable. */
    val localBatteryLevel: Int?,
    /** Display name of the local node, or null if not yet configured. */
    val localNodeName: String?,
)

/** Result of a [AiFunctionProvider.getNodeList] invocation. */
sealed class GetNodeListResult {
    /** Successfully retrieved the list of visible mesh nodes. */
    data class Success(val nodes: List<NodeSummary>) : GetNodeListResult()

    /** Device is not connected to a Meshtastic radio. */
    data class NotConnected(val message: String) : GetNodeListResult()

    /** An error occurred retrieving the node list. */
    data class Error(val reason: String) : GetNodeListResult()
}

/** Summary information for a single mesh node. */
data class NodeSummary(
    /** Node ID in Meshtastic hex format (e.g., "!abc12345"). */
    val id: String,
    /** Display name of the node. */
    val name: String,
    /** Battery level (0-100), or null if unavailable. */
    val batteryLevel: Int?,
    /** Last time this node was heard from (milliseconds since epoch). */
    val lastHeard: Long,
    /** Whether this node is currently considered online. */
    val isOnline: Boolean,
)

/** Result of a [AiFunctionProvider.getChannelInfo] invocation. */
sealed class GetChannelInfoResult {
    /** Successfully retrieved the list of channels. */
    data class Success(val channels: List<ChannelSummary>) : GetChannelInfoResult()

    /** Device is not connected to a Meshtastic radio. */
    data class NotConnected(val message: String) : GetChannelInfoResult()

    /** An error occurred retrieving channel info. */
    data class Error(val reason: String) : GetChannelInfoResult()
}

/** Summary information for a single mesh channel. */
data class ChannelSummary(
    /** Channel index (0-7). */
    val index: Int,
    /** Display name of the channel. */
    val name: String,
    /** Whether this is the primary/default channel. */
    val isPrimary: Boolean,
    /** Uplink enabled for this channel. */
    val uplinkEnabled: Boolean,
    /** Downlink enabled for this channel. */
    val downlinkEnabled: Boolean,
)

/** Result of a [AiFunctionProvider.getDeviceStatus] invocation. */
sealed class GetDeviceStatusResult {
    /** Successfully retrieved device status. */
    data class Success(val device: DeviceStatus) : GetDeviceStatusResult()

    /** Device is not available or not connected. */
    data class NotAvailable(val message: String) : GetDeviceStatusResult()

    /** An error occurred retrieving device status. */
    data class Error(val reason: String) : GetDeviceStatusResult()
}

/** Status and metrics of the local mesh radio device. */
data class DeviceStatus(
    /** Device model/hardware (e.g., "Meshtastic nRF52840"). */
    val model: String,
    /** Firmware version string. */
    val firmwareVersion: String,
    /** Battery level (0-100), or null if not battery-powered. */
    val batteryLevel: Int?,
    /** Charging status: "CHARGING", "NOT_CHARGING", or "UNKNOWN". */
    val chargingStatus: String,
    /** Display name of the device. */
    val deviceName: String?,
    /** Whether the radio is currently transmitting or receiving. */
    val isActive: Boolean,
)
