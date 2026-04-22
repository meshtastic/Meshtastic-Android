/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.firmware.ota.dfu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LegacyDfuProtocolTest {

    @Test
    fun `parse Success response`() {
        val r = LegacyDfuResponse.parse(byteArrayOf(0x10, 0x01, 0x01))
        assertIs<LegacyDfuResponse.Success>(r)
        assertEquals(LegacyDfuOpcode.START_DFU, r.requestOpcode)
    }

    @Test
    fun `parse Failure response`() {
        val r = LegacyDfuResponse.parse(byteArrayOf(0x10, 0x02, 0x06))
        assertIs<LegacyDfuResponse.Failure>(r)
        assertEquals(LegacyDfuOpcode.INIT_DFU_PARAMS, r.requestOpcode)
        assertEquals(LegacyDfuStatus.OPERATION_FAILED, r.status)
    }

    @Test
    fun `parse PacketReceipt - little endian uint32`() {
        // 0x12345678 = 305419896 LE: 78 56 34 12
        val r = LegacyDfuResponse.parse(byteArrayOf(0x11, 0x78, 0x56, 0x34, 0x12))
        assertIs<LegacyDfuResponse.PacketReceipt>(r)
        assertEquals(0x12345678L, r.bytesReceived)
    }

    @Test
    fun `parse PacketReceipt with high bit set treats value as unsigned`() {
        // 0xFF000000 should be parsed as 4278190080 (positive long), not -16777216 (negative int).
        val r = LegacyDfuResponse.parse(byteArrayOf(0x11, 0x00, 0x00, 0x00, 0xFF.toByte()))
        assertIs<LegacyDfuResponse.PacketReceipt>(r)
        assertEquals(0xFF000000L, r.bytesReceived)
    }

    @Test
    fun `parse Unknown for unrecognised prefix`() {
        val r = LegacyDfuResponse.parse(byteArrayOf(0x42, 0x99.toByte()))
        assertIs<LegacyDfuResponse.Unknown>(r)
    }

    @Test
    fun `parse Unknown for empty bytes`() {
        val r = LegacyDfuResponse.parse(byteArrayOf())
        assertIs<LegacyDfuResponse.Unknown>(r)
    }

    @Test
    fun `parse Unknown for too-short response`() {
        val r = LegacyDfuResponse.parse(byteArrayOf(0x10, 0x01))
        assertIs<LegacyDfuResponse.Unknown>(r)
    }

    @Test
    fun `parse Unknown for too-short packet receipt`() {
        val r = LegacyDfuResponse.parse(byteArrayOf(0x11, 0x01, 0x02))
        assertIs<LegacyDfuResponse.Unknown>(r)
    }

    @Test
    fun `legacyImageSizesPayload is 12 bytes LE - app only`() {
        // 0x1234 = 4660 → LE: 34 12 00 00
        val payload = legacyImageSizesPayload(appSize = 0x1234)
        assertEquals(12, payload.size)
        assertContentEquals(
            byteArrayOf(
                0,
                0,
                0,
                0, // softdevice
                0,
                0,
                0,
                0, // bootloader
                0x34,
                0x12,
                0,
                0, // app
            ),
            payload,
        )
    }

    @Test
    fun `legacyImageSizesPayload with all three components`() {
        val payload = legacyImageSizesPayload(appSize = 1, softDeviceSize = 2, bootloaderSize = 3)
        assertContentEquals(byteArrayOf(2, 0, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0), payload)
    }

    @Test
    fun `legacyPrnRequestPayload is opcode plus uint16 LE`() {
        val payload = legacyPrnRequestPayload(packets = 10)
        assertContentEquals(byteArrayOf(LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ, 10, 0), payload)
    }

    @Test
    fun `legacyPrnRequestPayload over 256 spans both bytes`() {
        val payload = legacyPrnRequestPayload(packets = 0x1234)
        assertContentEquals(byteArrayOf(LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ, 0x34, 0x12), payload)
    }
}
