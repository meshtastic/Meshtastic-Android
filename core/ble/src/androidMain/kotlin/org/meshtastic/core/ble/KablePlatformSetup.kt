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

import co.touchlab.kermit.Logger
import com.juul.kable.AndroidPeripheral
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.toIdentifier

internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // If we're connecting blindly to a bonded device without a fresh scan (DirectBleDevice),
    // we MUST use autoConnect = true. Otherwise, Android's direct connect algorithm will often fail
    // immediately with GATT 133 or timeout, especially if the device uses random resolvable addresses.
    // If we just scanned the device (KableBleDevice), direct connection (autoConnect = false) is faster.
    autoConnectIf(autoConnect)

    onServicesDiscovered {
        try {
            // Android defaults to 23 bytes MTU. Meshtastic packets can be 512 bytes.
            // Requesting the max MTU is critical for preventing dropped packets and stalls.
            @Suppress("MagicNumber")
            val negotiatedMtu = requestMtu(512)
            Logger.i { "Negotiated MTU: $negotiatedMtu" }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to request MTU" }
        }
    }
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    com.juul.kable.Peripheral(address.toIdentifier(), builderAction)

internal actual fun Peripheral.negotiatedMaxWriteLength(): Int? {
    val mtu = (this as? AndroidPeripheral)?.mtu?.value ?: return null
    return (mtu - 3).takeIf { it > 0 }
}
