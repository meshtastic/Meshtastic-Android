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
import co.touchlab.kermit.Logger

internal actual suspend fun Peripheral.platformConnectSetup() {
    // Simplified: No specific platform setup required initially.
}

internal actual fun PeripheralBuilder.platformConfig() {
    // Default to direct connections (autoConnect = false) which are much faster and more reliable
    // when connecting immediately after a scan.
    
    onServicesDiscovered {
        try {
            // Android defaults to 23 bytes MTU. Meshtastic packets can be 512 bytes.
            // Requesting the max MTU is critical for preventing dropped packets and stalls.
            val negotiatedMtu = requestMtu(512)
            Logger.i { "Negotiated MTU: $negotiatedMtu" }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to request MTU" }
        }
    }
}

internal actual fun createPeripheral(
    address: String,
    builderAction: PeripheralBuilder.() -> Unit
): Peripheral {
    val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        ?: error("Bluetooth not supported")
    val device = adapter.getRemoteDevice(address)
    return com.juul.kable.Peripheral(device, builderAction)
}
