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
import kotlinx.coroutines.flow.asStateFlow

/** Represents a BLE device known by address only (e.g. from bonded list) without an active advertisement. */
class DirectBleDevice(override val address: String, override val name: String? = null) : BleDevice {
    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    override val isBonded: Boolean = true

    override val isConnected: Boolean
        get() = _state.value is BleConnectionState.Connected || ActiveBleConnection.activeAddress == address

    @OptIn(com.juul.kable.ExperimentalApi::class)
    override suspend fun readRssi(): Int {
        val peripheral = ActiveBleConnection.activePeripheral
        return if (peripheral != null && ActiveBleConnection.activeAddress == address) {
            peripheral.rssi()
        } else {
            0
        }
    }

    override suspend fun bond() {
        // DirectBleDevice assumes we are already bonded.
    }

    fun updateState(newState: BleConnectionState) {
        _state.value = newState
    }
}
