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

import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import org.meshtastic.core.model.util.anonymize

// Web Bluetooth has no autoConnect, MTU, connection-priority or GATT-cache API.
internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) = Unit

/**
 * The granted peripheral for [address]. [builderAction] is not applied: the peripheral was configured when the picker
 * or `getDevices()` produced it, and Kable cannot rebuild it.
 */
internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    WebBluetoothGrants.find(address)
        ?: error("No Web Bluetooth grant for ${address.anonymize()}; pick the device again")

internal actual fun Peripheral.negotiatedMaxWriteLength(): Int? = null

internal actual fun Peripheral.requestHighConnectionPriority(): Boolean = false

internal actual fun Peripheral.requestBalancedConnectionPriority(): Boolean = false

internal actual fun Peripheral.refreshGattCache(): Boolean = false

// close() is terminal and only a new grant can replace the peripheral, so the grant keeps it open; disconnect() has
// already dropped the GATT link.
internal actual fun Peripheral.release() = Unit

// Kable's web rssi() needs watchAdvertisements(), which Chrome ships behind a flag and which waits on advertisements a
// connected radio may never send.
internal actual suspend fun Peripheral.connectedRssi(): Int? = null

// The picker or getDevices() built the peripheral and its observation handler, which records into the grant table.
internal actual fun Peripheral.preinstalledObservationFailure(): Throwable? =
    WebBluetoothGrants.observationFailure(identifier)

internal actual fun Peripheral.clearPreinstalledObservationFailure() =
    WebBluetoothGrants.clearObservationFailure(identifier)
