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

private val BLUEZ_DEVICE_SEGMENT = Regex("""/dev_([0-9A-Fa-f]{2}(?:_[0-9A-Fa-f]{2}){5})""", RegexOption.IGNORE_CASE)

/**
 * Reduces a BLE address to a comparable form. On Linux, Kable's JVM identifier is the BlueZ D-Bus object path (for
 * example `/org/bluez/hci0/dev_34_B7_DA_62_18_C5`, possibly wrapped in JSON), not the MAC, so the `dev_` segment is
 * rewritten to the colon-separated MAC. Every other form (MAC, Apple UUID) is only trimmed and upper-cased.
 */
fun normalizeBleAddress(address: String): String {
    val device = BLUEZ_DEVICE_SEGMENT.find(address)?.groupValues?.get(1)
    return (device?.replace('_', ':') ?: address).trim().uppercase()
}

/** True when [a] and [b] name the same peripheral, whether either is a MAC or a BlueZ object path. */
fun bleAddressesMatch(a: String, b: String): Boolean = normalizeBleAddress(a) == normalizeBleAddress(b)
