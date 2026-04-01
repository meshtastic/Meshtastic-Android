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

import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareHashUtilTest {

    @Test
    fun testBytesToHex() {
        val bytes = byteArrayOf(0x00, 0x1A, 0xFF.toByte(), 0xB3.toByte())
        val hex = FirmwareHashUtil.bytesToHex(bytes)
        assertEquals("001affb3", hex.lowercase())
    }

    @Test
    fun testSha256Calculation() {
        val data = "test_firmware_data".encodeToByteArray()
        val hashBytes = FirmwareHashUtil.calculateSha256Bytes(data)

        // Expected hash for "test_firmware_data"
        val expectedHex = "488e6c37c4c532bde9b92652a6a6312844d845a43015389ec74487b0eed38d09"
        assertEquals(expectedHex, FirmwareHashUtil.bytesToHex(hashBytes).lowercase())
    }
}
