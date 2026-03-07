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

import android.annotation.SuppressLint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.BondState
import no.nordicsemi.kotlin.ble.core.ConnectionState

/** An Android implementation of [BleDevice] that wraps a Nordic [Peripheral]. */
class AndroidBleDevice(val peripheral: Peripheral) : BleDevice {
    override val name: String?
        get() = peripheral.name

    override val address: String
        get() = peripheral.address

    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    @Suppress("MissingPermission")
    override val isBonded: Boolean
        get() = peripheral.bondState.value == BondState.BONDED

    override val isConnected: Boolean
        get() = peripheral.isConnected

    @SuppressLint("MissingPermission")
    override suspend fun readRssi(): Int = peripheral.readRssi()

    @SuppressLint("MissingPermission")
    override suspend fun bond() {
        peripheral.createBond()
    }

    /** Updates the connection state based on Nordic's [ConnectionState]. */
    fun updateState(nordicState: ConnectionState) {
        _state.value =
            when (nordicState) {
                is ConnectionState.Connecting -> BleConnectionState.Connecting
                is ConnectionState.Connected -> BleConnectionState.Connected
                is ConnectionState.Disconnecting -> BleConnectionState.Disconnecting
                is ConnectionState.Disconnected -> BleConnectionState.Disconnected
            }
    }
}
