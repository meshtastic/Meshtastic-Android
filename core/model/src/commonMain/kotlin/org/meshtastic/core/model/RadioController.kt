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
package org.meshtastic.core.model

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.ClientNotification

/**
 * Central interface for controlling the radio and mesh network.
 *
 * This component provides an abstraction over the underlying communication transport (e.g., BLE, Serial, TCP) and the
 * low-level mesh protocols. It allows feature modules to interact with the mesh without needing to know about
 * platform-specific service details or AIDL interfaces.
 */
@Suppress("TooManyFunctions")
interface RadioController {
    /** Reactive connection state of the radio. */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Flow of notifications from the radio client.
     *
     * These represent high-level events like "Handshake completed" or "Channel configuration updated."
     */
    val clientNotification: StateFlow<ClientNotification?>

    /**
     * Sends a data packet to the mesh.
     *
     * @param packet The [DataPacket] containing the payload and routing information.
     */
    suspend fun sendMessage(packet: DataPacket)

    /** Clears the current [clientNotification]. */
    fun clearClientNotification()

    /**
     * Toggles the favorite status of a node on the radio.
     *
     * @param nodeNum The node number to favorite/unfavorite.
     */
    suspend fun favoriteNode(nodeNum: Int)

    /**
     * Sends our shared contact information (identity and public key) to a remote node.
     *
     * @param nodeNum The destination node number.
     */
    suspend fun sendSharedContact(nodeNum: Int)

    /**
     * Updates the local radio configuration.
     *
     * @param config The new configuration [org.meshtastic.proto.Config].
     */
    suspend fun setLocalConfig(config: org.meshtastic.proto.Config)

    /**
     * Updates a local radio channel.
     *
     * @param channel The channel configuration [org.meshtastic.proto.Channel].
     */
    suspend fun setLocalChannel(channel: org.meshtastic.proto.Channel)

    /**
     * Updates the owner (user info) on a remote node.
     *
     * @param destNum The destination node number.
     * @param user The new user info [org.meshtastic.proto.User].
     * @param packetId The request packet ID.
     */
    suspend fun setOwner(destNum: Int, user: org.meshtastic.proto.User, packetId: Int)

    /**
     * Updates the general configuration on a remote node.
     *
     * @param destNum The destination node number.
     * @param config The new configuration [org.meshtastic.proto.Config].
     * @param packetId The request packet ID.
     */
    suspend fun setConfig(destNum: Int, config: org.meshtastic.proto.Config, packetId: Int)

    /**
     * Updates a module configuration on a remote node.
     *
     * @param destNum The destination node number.
     * @param config The new module configuration [org.meshtastic.proto.ModuleConfig].
     * @param packetId The request packet ID.
     */
    suspend fun setModuleConfig(destNum: Int, config: org.meshtastic.proto.ModuleConfig, packetId: Int)

    /**
     * Updates a channel configuration on a remote node.
     *
     * @param destNum The destination node number.
     * @param channel The new channel configuration [org.meshtastic.proto.Channel].
     * @param packetId The request packet ID.
     */
    suspend fun setRemoteChannel(destNum: Int, channel: org.meshtastic.proto.Channel, packetId: Int)

    /**
     * Sets a fixed position on a remote node.
     *
     * @param destNum The destination node number.
     * @param position The position to set.
     */
    suspend fun setFixedPosition(destNum: Int, position: Position)

    /**
     * Updates the notification ringtone on a remote node.
     *
     * @param destNum The destination node number.
     * @param ringtone The name/ID of the ringtone.
     */
    suspend fun setRingtone(destNum: Int, ringtone: String)

    /**
     * Updates the canned messages configuration on a remote node.
     *
     * @param destNum The destination node number.
     * @param messages The canned messages string.
     */
    suspend fun setCannedMessages(destNum: Int, messages: String)

    /**
     * Requests the current owner (user info) from a remote node.
     *
     * @param destNum The remote node number.
     * @param packetId The request packet ID.
     */
    suspend fun getOwner(destNum: Int, packetId: Int)

    /**
     * Requests a specific configuration section from a remote node.
     *
     * @param destNum The remote node number.
     * @param configType The numeric type of the configuration section.
     * @param packetId The request packet ID.
     */
    suspend fun getConfig(destNum: Int, configType: Int, packetId: Int)

    /**
     * Requests a module configuration section from a remote node.
     *
     * @param destNum The remote node number.
     * @param moduleConfigType The numeric type of the module configuration section.
     * @param packetId The request packet ID.
     */
    suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int)

    /**
     * Requests a specific channel configuration from a remote node.
     *
     * @param destNum The remote node number.
     * @param index The channel index.
     * @param packetId The request packet ID.
     */
    suspend fun getChannel(destNum: Int, index: Int, packetId: Int)

    /**
     * Requests the current ringtone from a remote node.
     *
     * @param destNum The remote node number.
     * @param packetId The request packet ID.
     */
    suspend fun getRingtone(destNum: Int, packetId: Int)

    /**
     * Requests the current canned messages from a remote node.
     *
     * @param destNum The remote node number.
     * @param packetId The request packet ID.
     */
    suspend fun getCannedMessages(destNum: Int, packetId: Int)

    /**
     * Requests the hardware connection status from a remote node.
     *
     * @param destNum The remote node number.
     * @param packetId The request packet ID.
     */
    suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int)

    /**
     * Commands a node to reboot.
     *
     * @param destNum The target node number.
     * @param packetId The request packet ID.
     */
    suspend fun reboot(destNum: Int, packetId: Int)

    /**
     * Commands a node to reboot into DFU (Device Firmware Update) mode.
     *
     * @param nodeNum The target node number.
     */
    suspend fun rebootToDfu(nodeNum: Int)

    /**
     * Initiates an Over-The-Air (OTA) reboot request.
     *
     * @param requestId The request ID.
     * @param destNum The target node number.
     * @param mode The OTA mode.
     * @param hash Optional hash for verification.
     */
    suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?)

    /**
     * Commands a node to shut down.
     *
     * @param destNum The target node number.
     * @param packetId The request packet ID.
     */
    suspend fun shutdown(destNum: Int, packetId: Int)

    /**
     * Performs a factory reset on a node.
     *
     * @param destNum The target node number.
     * @param packetId The request packet ID.
     */
    suspend fun factoryReset(destNum: Int, packetId: Int)

    /**
     * Resets the NodeDB on a node.
     *
     * @param destNum The target node number.
     * @param packetId The request packet ID.
     * @param preserveFavorites Whether to keep favorite nodes in the database.
     */
    suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean)

    /**
     * Removes a node from the mesh by its node number.
     *
     * @param packetId The request packet ID.
     * @param nodeNum The node number to remove.
     */
    suspend fun removeByNodenum(packetId: Int, nodeNum: Int)

    /**
     * Requests the current GPS position from a remote node.
     *
     * @param destNum The target node number.
     * @param currentPosition Our current position to provide in the request.
     */
    suspend fun requestPosition(destNum: Int, currentPosition: Position)

    /**
     * Requests detailed user info from a remote node.
     *
     * @param destNum The target node number.
     */
    suspend fun requestUserInfo(destNum: Int)

    /**
     * Initiates a traceroute request to a remote node.
     *
     * @param requestId The request ID.
     * @param destNum The destination node number.
     */
    suspend fun requestTraceroute(requestId: Int, destNum: Int)

    /**
     * Requests telemetry data from a remote node.
     *
     * @param requestId The request ID.
     * @param destNum The destination node number.
     * @param typeValue The numeric type of telemetry requested.
     */
    suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int)

    /**
     * Requests neighbor information (detected nodes) from a remote node.
     *
     * @param requestId The request ID.
     * @param destNum The destination node number.
     */
    suspend fun requestNeighborInfo(requestId: Int, destNum: Int)

    /**
     * Signals the start of a batch configuration session.
     *
     * @param destNum The target node number.
     */
    suspend fun beginEditSettings(destNum: Int)

    /**
     * Commits all pending configuration changes in a batch session.
     *
     * @param destNum The target node number.
     */
    suspend fun commitEditSettings(destNum: Int)

    /**
     * Generates a unique packet ID for a new request.
     *
     * @return A unique 32-bit integer.
     */
    fun getPacketId(): Int

    /** Starts providing the phone's location to the mesh. */
    fun startProvideLocation()

    /** Stops providing the phone's location to the mesh. */
    fun stopProvideLocation()

    /**
     * Changes the device address (e.g., BLE MAC, IP address) we are communicating with.
     *
     * @param address The new device identifier.
     */
    fun setDeviceAddress(address: String)
}
