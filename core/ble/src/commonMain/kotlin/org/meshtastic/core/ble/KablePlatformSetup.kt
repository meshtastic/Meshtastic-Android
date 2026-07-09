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

/** Platform-specific configuration for the Peripheral builder based on device type. */
internal expect fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean)

/** Platform-specific instantiation of a Peripheral by address. */
internal expect fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral

/**
 * Returns the negotiated maximum write payload length in bytes (i.e. ATT MTU minus the 3-byte ATT header), or `null` if
 * MTU has not yet been negotiated on this platform.
 */
internal expect fun Peripheral.negotiatedMaxWriteLength(): Int?

/**
 * Requests the highest-throughput BLE connection priority (smallest connection interval) supported by the platform.
 *
 * Returns `true` if the request was issued successfully. On platforms without an equivalent API (JVM/iOS) this is a
 * no-op returning `false`. Used by latency-sensitive flows such as DFU firmware streaming.
 */
internal expect fun Peripheral.requestHighConnectionPriority(): Boolean

/**
 * Requests balanced BLE connection priority (default ~30–50 ms interval) to reduce battery draw after latency-sensitive
 * operations complete. On platforms without an equivalent API (JVM/iOS) this is a no-op.
 */
internal expect fun Peripheral.requestBalancedConnectionPriority(): Boolean

/**
 * Clears the platform's cached GATT service table for the connected [Peripheral].
 *
 * Kable keeps Android's `BluetoothGatt` on an internal connection object owned by the peripheral. This extension
 * searches the peripheral and its active connection for that field, then invokes the hidden `refresh()` API to clear
 * the per-device service cache.
 *
 * Necessary when a device reboots into a different GATT profile (e.g., ESP32 OTA loader) using the same BLE MAC.
 *
 * Returns `true` if the cache was invalidated. On platforms without a cache (JVM/iOS) this is a no-op returning
 * `false`.
 */
internal expect fun Peripheral.refreshGattCache(): Boolean
