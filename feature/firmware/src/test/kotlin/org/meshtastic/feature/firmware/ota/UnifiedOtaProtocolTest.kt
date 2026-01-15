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
package org.meshtastic.feature.firmware.ota

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedOtaProtocolTest {

    @Test
    fun `OtaCommand StartOta produces correct command string`() {
        val size = 123456L
        val hash = "abc123def456"
        val command = OtaCommand.StartOta(size, hash)

        assertEquals("OTA 123456 abc123def456\n", command.toString())
    }

    @Test
    fun `OtaCommand StartOta handles large size and long hash`() {
        val size = 4294967295L
        val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val command = OtaCommand.StartOta(size, hash)

        assertEquals(
            "OTA 4294967295 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n",
            command.toString(),
        )
    }

    @Test
    fun `OtaResponse parse handles basic success cases`() {
        assertEquals(OtaResponse.Ok(), OtaResponse.parse("OK"))
        assertEquals(OtaResponse.Ok(), OtaResponse.parse("OK\n"))
        assertEquals(OtaResponse.Ack, OtaResponse.parse("ACK"))
        assertEquals(OtaResponse.Erasing, OtaResponse.parse("ERASING"))
    }

    @Test
    fun `OtaResponse parse handles detailed OK with version info`() {
        val response = OtaResponse.parse("OK 1.0 2.3.4 42 v2.3.4-abc123\n")

        assert(response is OtaResponse.Ok)
        val ok = response as OtaResponse.Ok
        assertEquals("1.0", ok.hwVersion)
        assertEquals("2.3.4", ok.fwVersion)
        assertEquals(42, ok.rebootCount)
        assertEquals("v2.3.4-abc123", ok.gitHash)
    }

    @Test
    fun `OtaResponse parse handles detailed OK with partial data`() {
        // Test with fewer than expected parts (should fallback to basic OK)
        val response = OtaResponse.parse("OK 1.0 2.3.4\n")
        assertEquals(OtaResponse.Ok(), response)
    }

    @Test
    fun `OtaResponse parse handles error cases`() {
        val err1 = OtaResponse.parse("ERR Hash Rejected")
        assert(err1 is OtaResponse.Error)
        assertEquals("Hash Rejected", (err1 as OtaResponse.Error).message)

        val err2 = OtaResponse.parse("ERR")
        assert(err2 is OtaResponse.Error)
        assertEquals("Unknown error", (err2 as OtaResponse.Error).message)
    }

    @Test
    fun `OtaResponse parse handles malformed or unexpected input`() {
        val response = OtaResponse.parse("RANDOM_GARBAGE")
        assert(response is OtaResponse.Error)
        assertEquals("Unknown response: RANDOM_GARBAGE", (response as OtaResponse.Error).message)
    }
}
