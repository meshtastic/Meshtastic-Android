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
 * Composite interface for all radio operations.
 *
 * Consumers should prefer the focused sub-interfaces (for example [MessageSender] and [RemoteAdmin]) for new code.
 * This super-interface remains for backward compatibility with existing injections.
 */
interface RadioController : MessageSender, DeviceAdmin, RemoteAdmin, DeviceControl, DataRequester {
    /**
     * Flow of notifications from the radio client.
     *
     * These represent high-level events like "Handshake completed" or "Channel configuration updated."
     */
    val clientNotification: StateFlow<ClientNotification?>

    /** Clears the current [clientNotification]. */
    fun clearClientNotification()

    /**
     * Toggles the favorite status of a node on the radio.
     *
     * @param nodeNum The node number to favorite/unfavorite.
     */
    suspend fun favoriteNode(nodeNum: Int)

    /**
     * Sends our shared contact information (identity and public key) to the firmware's NodeDB.
     *
     * This ensures the firmware has the correct public key for the destination node before a PKI-encrypted direct
     * message is sent. The method suspends until the radio acknowledges the admin packet.
     *
     * @param nodeNum The destination node number.
     * @return `true` if the radio accepted the contact, `false` on timeout or failure.
     */
    suspend fun sendSharedContact(nodeNum: Int): Boolean

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
