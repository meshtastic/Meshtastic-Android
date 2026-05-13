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
package org.meshtastic.core.repository

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.service.ServiceAction

/** Interface for handling UI-triggered actions and administrative commands for the mesh. */
@Suppress("TooManyFunctions")
interface MeshActionHandler {
    /** Processes a service action from the UI. */
    suspend fun onServiceAction(action: ServiceAction)

    /** Sets the owner of the local node. */
    fun handleSetOwner(u: MeshUser, myNodeNum: Int)

    /** Sends a data packet through the mesh. */
    fun handleSend(p: DataPacket, myNodeNum: Int)

    /** Requests the position of a remote node. */
    fun handleRequestPosition(destNum: Int, position: Position, myNodeNum: Int)

    /** Removes a node from the database by its node number. */
    fun handleRemoveByNodenum(nodeNum: Int, requestId: Int, myNodeNum: Int)

    /** Sets the owner of a remote node. */
    fun handleSetRemoteOwner(id: Int, destNum: Int, payload: ByteArray)

    /** Gets the owner of a remote node. */
    fun handleGetRemoteOwner(id: Int, destNum: Int)

    /** Sets the configuration of the local node. */
    fun handleSetConfig(payload: ByteArray, myNodeNum: Int)

    /** Sets the configuration of a remote node. */
    fun handleSetRemoteConfig(id: Int, destNum: Int, payload: ByteArray)

    /** Gets the configuration of a remote node. */
    fun handleGetRemoteConfig(id: Int, destNum: Int, config: Int)

    /** Sets the module configuration of a remote node. */
    fun handleSetModuleConfig(id: Int, destNum: Int, payload: ByteArray)

    /** Gets the module configuration of a remote node. */
    fun handleGetModuleConfig(id: Int, destNum: Int, config: Int)

    /** Sets the ringtone of a remote node. */
    fun handleSetRingtone(destNum: Int, ringtone: String)

    /** Gets the ringtone of a remote node. */
    fun handleGetRingtone(id: Int, destNum: Int)

    /** Sets canned messages on a remote node. */
    fun handleSetCannedMessages(destNum: Int, messages: String)

    /** Gets canned messages from a remote node. */
    fun handleGetCannedMessages(id: Int, destNum: Int)

    /** Sets a channel configuration on the local node. */
    fun handleSetChannel(payload: ByteArray?, myNodeNum: Int)

    /** Sets a channel configuration on a remote node. */
    fun handleSetRemoteChannel(id: Int, destNum: Int, payload: ByteArray?)

    /** Gets a channel configuration from a remote node. */
    fun handleGetRemoteChannel(id: Int, destNum: Int, index: Int)

    /** Requests neighbor information from a remote node. */
    fun handleRequestNeighborInfo(requestId: Int, destNum: Int)

    /** Begins editing settings on a remote node. */
    fun handleBeginEditSettings(destNum: Int)

    /** Commits settings edits on a remote node. */
    fun handleCommitEditSettings(destNum: Int)

    /** Reboots a remote node into DFU mode. */
    fun handleRebootToDfu(destNum: Int)

    /** Requests telemetry from a remote node. */
    fun handleRequestTelemetry(requestId: Int, destNum: Int, type: Int)

    /** Requests a remote node to shut down. */
    fun handleRequestShutdown(requestId: Int, destNum: Int)

    /** Requests a remote node to reboot. */
    fun handleRequestReboot(requestId: Int, destNum: Int)

    /** Requests a remote node to reboot in OTA mode. */
    fun handleRequestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?)

    /** Requests a factory reset on a remote node. */
    fun handleRequestFactoryReset(requestId: Int, destNum: Int)

    /** Requests a node database reset on a remote node. */
    fun handleRequestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean)

    /** Gets the connection status of a remote node. */
    fun handleGetDeviceConnectionStatus(requestId: Int, destNum: Int)

    /** Updates the last used device address. */
    fun handleUpdateLastAddress(deviceAddr: String?)
}
