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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId

/**
 * Thin interface exposing device-address and connection-management operations to feature modules.
 *
 * The SDK now owns the raw transport (BLE, TCP, Serial). This interface retains only the device-selection
 * surface that Scanner and connection UIs require.
 */
interface RadioInterfaceService {
    /** The device types supported by this platform's radio interface. */
    val supportedDeviceTypes: List<DeviceType>

    /** Flow of the current device address (e.g. "x0123456789AB" for BLE, "tTCP:192.168.1.1" for TCP). */
    val currentDeviceAddressFlow: StateFlow<String?>

    /** Whether we are currently using a mock transport. */
    fun isMockTransport(): Boolean

    /** Returns the current device address. */
    fun getDeviceAddress(): String?

    /** Sets the device address to connect to. Returns true if the address changed. */
    fun setDeviceAddress(deviceAddr: String?): Boolean

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Initiates connection to the radio at the current device address. */
    fun connect()

    /** Disconnects from the radio. */
    suspend fun disconnect()
}
