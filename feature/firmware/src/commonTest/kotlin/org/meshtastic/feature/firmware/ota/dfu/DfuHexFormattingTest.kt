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

/** Pins the zero-padded lowercase hex that DFU error messages carry into logs and bug reports. */
class DfuHexFormattingTest {

    @Test
    fun `unknown legacy status is two padded hex digits`() {
        assertEquals("UNKNOWN(0x0a)", LegacyDfuStatus.describe(0x0A))
        assertEquals("UNKNOWN(0xff)", LegacyDfuStatus.describe(0xFF.toByte()))
    }

    @Test
    fun `unknown extended error is two padded hex digits`() {
        assertEquals("Unknown extended error 0x7f", DfuExtendedError.describe(0x7F))
    }

    @Test
    fun `protocol error names opcode and result as padded hex`() {
        assertEquals(
            "DFU protocol error: opcode=0x01 result=0x0b",
            DfuException.ProtocolError(opcode = 0x01, resultCode = 0x0B).message,
        )
    }

    @Test
    fun `checksum mismatch prints both crcs as eight hex digits`() {
        assertEquals(
            "CRC-32 mismatch: expected 0x00000abc got 0xffffffff",
            DfuException.ChecksumMismatch(expected = 0xABC, actual = -1).message,
        )
    }
}
