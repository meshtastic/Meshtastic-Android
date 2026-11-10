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

import kotlin.uuid.Uuid

/** Nordic DFU facts shared by the firmware updater and the stranded-bootloader scan. */
object NordicDfu {
    /** Legacy DFU service, advertised by the Meshtastic nRF52 bootloader while it waits for a BLE OTA. */
    val LEGACY_SERVICE_UUID: Uuid = Uuid.parse("00001530-1212-EFDE-1523-785FEABCD123")

    private const val MAC_PARTS_COUNT = 6
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xFF

    /**
     * The address a device advertises from once it reboots into its update mode: the last byte of [macAddress] plus
     * one, wrapping at `FF`. Returns [macAddress] unchanged when it is not a six-part hex MAC.
     */
    @Suppress("ReturnCount")
    fun bootloaderAddress(macAddress: String): String {
        val parts = macAddress.split(":")
        if (parts.size != MAC_PARTS_COUNT) return macAddress
        val lastByte = parts[MAC_PARTS_COUNT - 1].toIntOrNull(HEX_RADIX) ?: return macAddress
        val incremented = ((lastByte + 1) and BYTE_MASK).toString(HEX_RADIX).uppercase().padStart(2, '0')
        return "${parts.take(MAC_PARTS_COUNT - 1).joinToString(":")}:$incremented"
    }
}
