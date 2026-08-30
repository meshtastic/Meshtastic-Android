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

/**
 * [BleDevice] wrapping the [JsBluetoothDevice] handle returned by `navigator.bluetooth.requestDevice()`.
 *
 * Web Bluetooth cannot reconnect to a device from an address string alone: `BluetoothRemoteGATTServer.connect()` needs
 * the live [JsBluetoothDevice] object the picker returned, so this class carries that handle end to end — mirroring how
 * the Kable-based `MeshtasticBleDevice` (in `nonWebMain`) carries a Kable `Advertisement`. [WebBleConnection.connect]
 * casts to this type, matching that same pattern.
 *
 * @param jsDevice The live device handle from `requestDevice()`. Internal so only this file's siblings
 *   ([WebBleConnection], [WebBleScanner]) can reach into it — everything else in `core:ble` only ever sees the
 *   [BleDevice] contract.
 */
class WebBleDevice(internal val jsDevice: JsBluetoothDevice) : BleDevice {
    override val address: String = jsDevice.id
    override val name: String? = jsDevice.name

    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    // Web Bluetooth has no OS-level pairing/bonding concept the page can query or drive — see
    // WebBluetoothRepository's KDoc for the full rationale.
    override val isBonded: Boolean = false

    override val isConnected: Boolean
        get() = _state.value is BleConnectionState.Connected

    // Web Bluetooth exposes no live RSSI query for an already-connected device (advertisement-time RSSI isn't
    // available either: requestDevice()'s picker UI is browser-native, not something this code observes).
    override suspend fun readRssi(): Int? = null

    override suspend fun bond() {
        // No-op: Web Bluetooth has no application-initiated bonding API. See WebBluetoothRepository.
    }

    /** Updates the tracked connection state. Called by [WebBleConnection] when the GATT connection state changes. */
    internal fun updateState(newState: BleConnectionState) {
        _state.value = newState
    }
}
