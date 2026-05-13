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

import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId

/**
 * Creates [RadioTransport] instances for specific device addresses.
 *
 * Implemented per-platform to provide the correct hardware transport (BLE, Serial, TCP).
 */
interface RadioTransportFactory {
    /** The device types supported by this factory. */
    val supportedDeviceTypes: List<DeviceType>

    /** Whether we are currently forced into using a mock transport (e.g., Firebase Test Lab). */
    fun isMockTransport(): Boolean

    /** Creates a transport for the given [address], or a NOP implementation if invalid/unsupported. */
    fun createTransport(address: String, service: RadioInterfaceService): RadioTransport

    /** Checks if the given [address] represents a valid, supported transport type. */
    fun isAddressValid(address: String?): Boolean

    /** Constructs a full radio address for the specific [interfaceId] and [rest] identifier. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String
}
