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

class KableBleDevice(val advertisement: Advertisement) : BleDevice {
    override val name: String?
        get() = advertisement.name

    override val address: String
        get() = advertisement.identifier.toString()

    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val state: StateFlow<BleConnectionState> = _state

    // Scanned devices can be connected directly without an explicit bonding step.
    // On Android, Kable's connectGatt triggers the OS pairing dialog transparently
    // when the firmware requires an encrypted link. On Desktop, btleplug delegates
    // to the OS Bluetooth stack which handles pairing the same way.
    // The BleRadioInterface.connect() reconnection path has a separate isBonded
    // check for the case where a previously bonded device loses its bond.
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
        // Bonding for scanned devices is handled at the BluetoothRepository level
        // (Android) or by the OS during GATT connection (Desktop/JVM).
    }

    internal fun updateState(newState: BleConnectionState) {
        _state.value = newState
    }
}
