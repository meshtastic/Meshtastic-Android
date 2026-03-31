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
package org.meshtastic.feature.firmware.ota.dfu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DfuResponseTest {

    @Test
    fun parseSuccessResponse() {
        // [0x60, OPCODE, SUCCESS]
        val data = byteArrayOf(0x60, 0x01, 0x01)
        val response = DfuResponse.parse(data)

        assertTrue(response is DfuResponse.Success)
        assertEquals(0x01, response.opcode)
    }

    @Test
    fun parseFailureResponse() {
        // [0x60, OPCODE, ERROR_CODE]
        // 0x01 (CREATE) failed with 0x03 (INVALID_PARAMETER)
        val data = byteArrayOf(0x60, 0x01, 0x03)
        val response = DfuResponse.parse(data)

        assertTrue(response is DfuResponse.Failure)
        assertEquals(0x01, response.opcode)
        assertEquals(0x03, response.resultCode)
    }

    @Test
    fun parseSelectResultResponse() {
        // [0x60, 0x06, 0x01, max_size(4), offset(4), crc(4)]
        // maxSize = 0x00000100 (256)
        // offset = 0x00000080 (128)
        // crc = 0x0000ABCD (43981)
        val data =
            byteArrayOf(
                0x60,
                0x06,
                0x01,
                0x00,
                0x01,
                0x00,
                0x00, // maxSize: 256
                0x80.toByte(),
                0x00,
                0x00,
                0x00, // offset: 128
                0xCD.toByte(),
                0xAB.toByte(),
                0x00,
                0x00, // crc: 43981
            )
        val response = DfuResponse.parse(data)

        assertTrue(response is DfuResponse.SelectResult)
        assertEquals(0x06, response.opcode)
        assertEquals(256, response.maxSize)
        assertEquals(128, response.offset)
        assertEquals(43981, response.crc32)
    }

    @Test
    fun parseChecksumResultResponse() {
        // [0x60, 0x03, 0x01, offset(4), crc(4)]
        // offset = 1024
        // crc = 0x12345678 (305419896)
        val data =
            byteArrayOf(
                0x60,
                0x03,
                0x01,
                0x00,
                0x04,
                0x00,
                0x00, // offset: 1024
                0x78,
                0x56,
                0x34,
                0x12, // crc: 0x12345678
            )
        val response = DfuResponse.parse(data)

        assertTrue(response is DfuResponse.ChecksumResult)
        assertEquals(1024, response.offset)
        assertEquals(0x12345678, response.crc32)
    }

    @Test
    fun parseUnknownResponse() {
        // First byte is not 0x60
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        assertTrue(DfuResponse.parse(data1) is DfuResponse.Unknown)

        // Less than 3 bytes
        val data2 = byteArrayOf(0x60, 0x01)
        assertTrue(DfuResponse.parse(data2) is DfuResponse.Unknown)
    }
}
