/*
 * Copyright (c) 2025 Meshtastic LLC
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity

/**
 * Interface for the low-level radio interface that handles raw byte communication.
 */
interface RadioInterfaceService {
    /** Reactive connection state of the radio. */
    val connectionState: StateFlow<ConnectionState>

    /** Flow of the current device address. */
    val currentDeviceAddressFlow: StateFlow<String?>

    /** Whether we are currently using a mock interface. */
    fun isMockInterface(): Boolean

    /** Flow of raw data received from the radio. */
    val receivedData: SharedFlow<ByteArray>

    /** Flow of radio activity events. */
    val meshActivity: SharedFlow<MeshActivity>

    /** Sends a raw byte array to the radio. */
    fun sendToRadio(bytes: ByteArray)

    /** Initiates the connection to the radio. */
    fun connect()

    /** Returns the current device address. */
    fun getDeviceAddress(): String?

    /** Sets the device address to connect to. */
    fun setDeviceAddress(deviceAddr: String?): Boolean

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Called by an interface when it has successfully connected. */
    fun onConnect()

    /** Called by an interface when it has disconnected. */
    fun onDisconnect(isPermanent: Boolean)

    /** Called by an interface when it has disconnected with an error. */
    fun onDisconnect(error: Any)

    /** Called by an interface when it has received raw data from the radio. */
    fun handleFromRadio(bytes: ByteArray)

    /** The scope in which interface-related coroutines should run. */
    val serviceScope: CoroutineScope
}
