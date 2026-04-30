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
package org.meshtastic.feature.firmware.ota

import okio.ByteString.Companion.toByteString

/** KMP utility functions for firmware hash calculation. */
object FirmwareHashUtil {

    /**
     * Calculate SHA-256 hash of raw bytes.
     *
     * @param data Firmware bytes to hash
     * @return 32-byte SHA-256 hash
     */
    fun calculateSha256Bytes(data: ByteArray): ByteArray = data.toByteString().sha256().toByteArray()

    /** Convert byte array to lowercase hex string. */
    fun bytesToHex(bytes: ByteArray): String = bytes.toByteString().hex()
}
