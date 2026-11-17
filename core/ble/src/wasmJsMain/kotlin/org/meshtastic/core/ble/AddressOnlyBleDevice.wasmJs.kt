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
package org.meshtastic.core.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Web Bluetooth reaches a device only through the picker, so an address alone never connects. */
actual fun addressOnlyBleDevice(address: String): BleDevice = AddressOnlyBleDevice(address)

private class AddressOnlyBleDevice(override val address: String) : BleDevice {
    override val name: String? = null
    override val state: StateFlow<BleConnectionState> = MutableStateFlow(BleConnectionState.Disconnected())
    override val isBonded: Boolean = false
    override val isConnected: Boolean = false

    override suspend fun readRssi(): Int? = null

    override suspend fun bond() = Unit
}
