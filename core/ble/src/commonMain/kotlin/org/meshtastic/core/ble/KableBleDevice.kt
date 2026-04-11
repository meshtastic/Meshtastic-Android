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

import com.juul.kable.Advertisement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KableBleDevice(val advertisement: Advertisement) : BleDevice {
    override val name: String?
        get() = advertisement.name

    override val address: String
        get() = advertisement.identifier.toString()

    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    // Bonding is handled by the OS pairing dialog on Android; on desktop Kable connects directly.
    override val isBonded: Boolean = true

    override val isConnected: Boolean
        get() = _state.value is BleConnectionState.Connected || ActiveBleConnection.activeAddress == address

    @OptIn(com.juul.kable.ExperimentalApi::class)
    override suspend fun readRssi(): Int {
        val peripheral = ActiveBleConnection.activePeripheral
        return if (peripheral != null && ActiveBleConnection.activeAddress == address) {
            peripheral.rssi()
        } else {
            advertisement.rssi
        }
    }

    override suspend fun bond() {
        // No-op: bonding is OS-managed on Android and not required on desktop.
    }

    internal fun updateState(newState: BleConnectionState) {
        _state.value = newState
    }
}
