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

import no.nordicsemi.kotlin.ble.client.android.Peripheral
import org.meshtastic.core.model.util.anonymize

/** A snapshot in time of the state of the bluetooth subsystem. */
data class BluetoothState(
    /** Whether we have adequate permissions to query bluetooth state */
    val hasPermissions: Boolean = false,
    /** If we have adequate permissions and bluetooth is enabled */
    val enabled: Boolean = false,
    /** If enabled, a list of the currently bonded devices */
    val bondedDevices: List<Peripheral> = emptyList(),
) {
    override fun toString(): String =
        "BluetoothState(hasPermissions=$hasPermissions, enabled=$enabled, bondedDevices=${bondedDevices.map {
            it.anonymize
        }})"
}
