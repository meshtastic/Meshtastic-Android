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
package org.meshtastic.core.service

import org.meshtastic.core.common.util.isValidDeviceAddress

/** Prefix the app uses for a persisted BLE device address. TCP is `t`, USB is `s`. */
private const val BLE_ADDRESS_PREFIX = 'x'

/** What a boot / package-replaced broadcast should do about the previously selected device. */
enum class BootReconnectDecision {
    /** Bring the mesh service up and reconnect. */
    START_SERVICE,

    /** Nothing was ever selected, so there is nothing to reconnect to. */
    NO_DEVICE,

    /**
     * A BLE device is selected but the Bluetooth permission is gone — revoked in system settings, or never granted
     * before the app was last closed. Starting the service would produce an endless, invisible retry loop.
     */
    BLE_PERMISSION_MISSING,
}

/**
 * Decides what an unattended reconnect should do, given the persisted device and the current permission.
 *
 * The permission check exists because the failure it prevents is completely silent. Kable reports a missing Bluetooth
 * permission as `UnmetRequirementException`, which the transport classifies as *transient* — correctly, since a revoked
 * permission can come back — and the transient path drops the message. With `maxFailures = Int.MAX_VALUE` there is no
 * give-up path to escalate to, so the service would retry forever, log a warning nobody reads, and show a foreground
 * notification saying only "Disconnected". No user is present at boot to notice, and nothing tells them afterwards.
 *
 * Only BLE is gated. A TCP or USB device needs no Bluetooth permission, and refusing to start for those would break
 * reconnection for users who have deliberately never granted it.
 *
 * Pure and platform-agnostic so the decision is unit-testable without a `BroadcastReceiver` or a permission grant.
 */
fun bootReconnectDecision(address: String?, hasBluetoothPermission: Boolean): BootReconnectDecision = when {
    !isValidDeviceAddress(address) -> BootReconnectDecision.NO_DEVICE

    address?.firstOrNull() == BLE_ADDRESS_PREFIX && !hasBluetoothPermission ->
        BootReconnectDecision.BLE_PERMISSION_MISSING

    else -> BootReconnectDecision.START_SERVICE
}
