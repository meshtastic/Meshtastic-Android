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
 * Base implementation of [BleDevice] that consolidates shared state management, connection tracking, and RSSI reading.
 *
 * Subclasses only need to provide [name], [address], and [fallbackRssi] — everything else (state flow, bonded flag,
 * connection check, live RSSI via the active peripheral, bond no-op) is handled here.
 */
abstract class BaseBleDevice : BleDevice {
    private val _state = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    // Bonding is handled by the OS pairing dialog on Android; on desktop Kable connects directly.
    override val isBonded: Boolean = true

    override val isConnected: Boolean
        get() = _state.value is BleConnectionState.Connected || ActiveBleConnection.active?.address == address

    /**
     * Returns the RSSI value to use when no active peripheral is available (e.g. the cached advertisement RSSI for
     * scanned devices, or 0 for bonded-only devices).
     */
    protected abstract fun fallbackRssi(): Int

    @OptIn(com.juul.kable.ExperimentalApi::class)
    override suspend fun readRssi(): Int {
        val active = ActiveBleConnection.active
        return if (active != null && active.address == address) {
            active.peripheral.rssi()
        } else {
            fallbackRssi()
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
