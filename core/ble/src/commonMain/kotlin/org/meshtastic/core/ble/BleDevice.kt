/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ble

import kotlinx.coroutines.flow.StateFlow

/** Represents a BLE device. */
interface BleDevice {
    /** The device's name. */
    val name: String?

    /** The device's address. */
    val address: String

    /** The current connection state of the device. */
    val state: StateFlow<BleConnectionState>

    /** Whether the device is bonded. */
    val isBonded: Boolean

    /** Whether the device is currently connected. */
    val isConnected: Boolean

    /**
     * The RSSI reported by the most recent scan advertisement for this device, in dBm.
     *
     * `null` for devices that have not been observed via a scan (e.g. bonded-only devices retrieved from the OS). This
     * is a snapshot — to see live updates, observe a flow of [BleDevice] instances from [BleScanner].
     */
    val rssi: Int?
        get() = null

    /** Reads the current RSSI value. */
    suspend fun readRssi(): Int

    /** Bond the device. */
    suspend fun bond()
}
