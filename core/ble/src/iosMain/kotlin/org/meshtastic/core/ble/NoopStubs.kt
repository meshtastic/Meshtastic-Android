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
import com.juul.kable.ScannerBuilder

// Kable's `Filter.Address` throws UnsupportedOperationException on Apple.
internal actual val supportsNativeAddressScanFilter: Boolean = false

// CoreBluetooth identifies a peripheral by a per-host UUID, never its MAC.
internal actual val bleAddressIsHardware: Boolean = false

internal actual fun ScannerBuilder.platformScanConfig() {
    // No-op: preConflate is Android-only.
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    throw UnsupportedOperationException("iOS Peripheral not yet implemented")

internal actual fun Peripheral.negotiatedMaxWriteLength(): Int? = null
