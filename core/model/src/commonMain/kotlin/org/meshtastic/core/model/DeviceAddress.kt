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
package org.meshtastic.core.model

import org.meshtastic.core.common.util.isValidDeviceAddress

/**
 * A selected-radio address, parsed from its stored form: one [InterfaceId] character followed by the transport's own
 * identity for the device.
 *
 * [raw] is the persisted string and stays the key for the per-device database and every per-device preference, so it is
 * never rewritten. The legacy `!` BLE prefix therefore keeps its `!` in [raw] and only reads as
 * [InterfaceId.BLUETOOTH].
 */
class DeviceAddress private constructor(val raw: String, val interfaceId: InterfaceId) {

    /** The address without its transport prefix: a BLE MAC, a `host:port`, a USB serial key. */
    val identity: String
        get() = raw.substring(1)

    override fun equals(other: Any?): Boolean = other is DeviceAddress && other.raw == raw

    override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = "DeviceAddress($interfaceId)"

    companion object {
        private const val LEGACY_BLUETOOTH_PREFIX = '!'

        /**
         * Parses [raw], or returns `null` for no selection: `null`, blank, any no-device sentinel, or an unknown
         * prefix.
         */
        fun parse(raw: String?): DeviceAddress? {
            if (raw == null || !isValidDeviceAddress(raw)) return null
            val prefix = raw.first()
            val interfaceId =
                if (prefix == LEGACY_BLUETOOTH_PREFIX) InterfaceId.BLUETOOTH else InterfaceId.forIdChar(prefix)
            return interfaceId?.takeUnless { it == InterfaceId.NOP }?.let { DeviceAddress(raw, it) }
        }
    }
}
