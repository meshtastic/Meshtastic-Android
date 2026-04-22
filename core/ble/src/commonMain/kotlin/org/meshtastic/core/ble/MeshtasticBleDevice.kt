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
import com.juul.kable.ExperimentalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unified [BleDevice] implementation for all BLE devices — scanned, bonded, or both.
 *
 * When created from a live BLE scan, [advertisement] is populated and used for optimal peripheral construction via
 * `Peripheral(advertisement)`. When created from the OS bonded device list (address only), [advertisement] is `null`
 * and the peripheral is constructed via `createPeripheral(address)` with `autoConnect = true`.
 *
 * @param address The device's MAC address (or platform identifier string).
 * @param name The device's display name, if known.
 * @param advertisement The Kable [Advertisement] from a live scan, or `null` for bonded-only devices.
 */
class MeshtasticBleDevice(
    override val address: String,
    override val name: String? = null,
    val advertisement: Advertisement? = null,
) : BleDevice {

    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    // Bonding is handled by the OS pairing dialog on Android; on desktop Kable connects directly.
    override val isBonded: Boolean = true

    override val isConnected: Boolean
        get() = _state.value is BleConnectionState.Connected || ActiveBleConnection.active?.address == address

    override val rssi: Int? = advertisement?.rssi

    @OptIn(ExperimentalApi::class)
    override suspend fun readRssi(): Int {
        val active = ActiveBleConnection.active
        return if (active != null && active.address == address) {
            active.peripheral.rssi()
        } else {
            advertisement?.rssi ?: 0
        }
    }

    override suspend fun bond() {
        // No-op: bonding is OS-managed on Android and not required on desktop.
    }

    /** Updates the tracked connection state. Called by [KableBleConnection] when the peripheral state changes. */
    internal fun updateState(newState: BleConnectionState) {
        _state.value = newState
    }
}
