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
import com.juul.kable.PooledThreadingStrategy
import com.juul.kable.toIdentifier

/**
 * Shared thread pool for Kable BLE connections.
 *
 * [PooledThreadingStrategy] reuses handler threads across reconnect cycles, avoiding the overhead of creating a new
 * thread per connection attempt that [OnDemandThreadingStrategy][com.juul.kable.OnDemandThreadingStrategy] incurs. Idle
 * threads are evicted after 1 minute (default).
 *
 * A single app-wide instance is used because Kable recommends exactly one pool per application.
 */
private val sharedThreadingStrategy = PooledThreadingStrategy()

internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // Bonded devices without a fresh advertisement must use autoConnect = true. Otherwise,
    // Android's direct connect algorithm often fails with GATT 133 or times out, especially
    // if the device uses random resolvable addresses. Scanned devices (advertisement != null)
    // use direct connection (autoConnect = false) for faster initial connects.
    autoConnectIf(autoConnect)

    threadingStrategy = sharedThreadingStrategy

    // We intentionally keep Kable's defaults for `transport` (Le) and `phy` (Le1M).
    // Meshtastic radios (nRF52, ESP32-S3, RP2040+nRF) advertise BLE-only and don't support
    // the LE 2M PHY in any first-party firmware, so changing these would be a regression risk
    // with no upside. If a future hardware revision exposes 2M PHY, override `phy = Phy.Le2M`
    // here after confirming the firmware advertises it.

    onServicesDiscovered {
        try {
            // Android defaults to 23 bytes MTU. Meshtastic packets can be 512 bytes.
            // Requesting the max MTU is critical for preventing dropped packets and stalls.
            @Suppress("MagicNumber")
            val negotiatedMtu = requestMtu(512)
            Logger.i { "[${device.address}] Negotiated MTU: $negotiatedMtu" }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "[${device.address}] Failed to request MTU" }
        }
    }
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    com.juul.kable.Peripheral(address.toIdentifier(), builderAction)

/** ATT protocol header size (opcode + handle) subtracted from MTU to get the usable payload. */
private const val ATT_HEADER_SIZE = 3

internal actual fun Peripheral.negotiatedMaxWriteLength(): Int? {
    val mtu = (this as? AndroidPeripheral)?.mtu?.value ?: return null
    return (mtu - ATT_HEADER_SIZE).takeIf { it > 0 }
}

internal actual fun Peripheral.requestHighConnectionPriority(): Boolean {
    val androidPeripheral = this as? AndroidPeripheral ?: return false
    return runCatching { androidPeripheral.requestConnectionPriority(AndroidPeripheral.Priority.High) }
        .onFailure { Logger.w(it) { "requestConnectionPriority(High) threw" } }
        .getOrDefault(false)
}
