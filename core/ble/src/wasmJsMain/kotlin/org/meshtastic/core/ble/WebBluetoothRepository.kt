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
import org.koin.core.annotation.Single

/**
 * [BluetoothRepository] implementation backed by `navigator.bluetooth.getAvailability()`.
 *
 * Web Bluetooth has **no OS-level bonding/pairing concept** the page can query, initiate, or remove — every connection
 * is authorized per-request by the user through [WebBleScanner]'s device picker, and there is nothing durable stored on
 * the device side for this code to inspect afterwards. [isBonded] and [bond] are therefore honestly `false`/no-op
 * rather than inventing API surface the platform doesn't have; [removeBond] is left at the [BluetoothRepository]
 * interface's own default no-op for the same reason.
 *
 * "Permission" on web is coarser than Android's runtime permissions too: there is no separate grant to check ahead of a
 * scan the way [BluetoothState.hasPermissions] models on Android — the picker *is* the permission grant, each time.
 * [refreshState] reports `hasPermissions = true` whenever the API is present at all, deferring the real per-connection
 * consent question to the picker itself rather than pre-empting it here.
 */
@Single
class WebBluetoothRepository : BluetoothRepository {
    private val _state = MutableStateFlow(BluetoothState())
    override val state: StateFlow<BluetoothState> = _state

    override fun refreshState() {
        val available = webBluetoothOrNull() != null
        _state.value = BluetoothState(hasPermissions = available, enabled = available, bondedDevices = emptyList())
    }

    override fun isValid(bleAddress: String): Boolean = bleAddress.isNotEmpty()

    override fun isBonded(address: String): Boolean = false

    override suspend fun bond(device: BleDevice) {
        // No-op: see class KDoc. Web Bluetooth's device picker is itself the only "pairing" step that exists.
    }
}
