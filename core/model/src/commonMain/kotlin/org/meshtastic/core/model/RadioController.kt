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

interface RadioController {
    val connectionState: StateFlow<ConnectionState>
    val clientNotification: StateFlow<ClientNotification?>

    suspend fun sendMessage(packet: DataPacket)
    fun clearClientNotification()

    // Abstracted ServiceActions
    suspend fun favoriteNode(nodeNum: Int)
    suspend fun sendSharedContact(nodeNum: Int)

    // Radio configuration
    suspend fun setOwner(destNum: Int, user: org.meshtastic.proto.User, packetId: Int)
    suspend fun setConfig(destNum: Int, config: org.meshtastic.proto.Config, packetId: Int)
    suspend fun setModuleConfig(destNum: Int, config: org.meshtastic.proto.ModuleConfig, packetId: Int)
    suspend fun setRemoteChannel(destNum: Int, channel: org.meshtastic.proto.Channel, packetId: Int)
    suspend fun setFixedPosition(destNum: Int, position: Position)
    suspend fun setRingtone(destNum: Int, ringtone: String)
    suspend fun setCannedMessages(destNum: Int, messages: String)

    // Admin get operations
    suspend fun getOwner(destNum: Int, packetId: Int)
    suspend fun getConfig(destNum: Int, configType: Int, packetId: Int)
    suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int)
    suspend fun getChannel(destNum: Int, index: Int, packetId: Int)
    suspend fun getRingtone(destNum: Int, packetId: Int)
    suspend fun getCannedMessages(destNum: Int, packetId: Int)
    suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int)

    // Admin operations
    suspend fun reboot(destNum: Int, packetId: Int)
    suspend fun shutdown(destNum: Int, packetId: Int)
    suspend fun factoryReset(destNum: Int, packetId: Int)
    suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean)
    suspend fun removeByNodenum(packetId: Int, nodeNum: Int)

    // Batch editing
    suspend fun beginEditSettings(destNum: Int)
    suspend fun commitEditSettings(destNum: Int)

    // Helpers
    fun getPacketId(): Int

    /**
     * Starts providing the phone's location to the mesh.
     */
    fun startProvideLocation()

    /**
     * Stops providing the phone's location to the mesh.
     */
    fun stopProvideLocation()
}
