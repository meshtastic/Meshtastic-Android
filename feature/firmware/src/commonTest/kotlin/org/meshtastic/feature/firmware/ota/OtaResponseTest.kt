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
import kotlin.test.assertTrue

class OtaResponseTest {

    @Test
    fun parseSimpleOk() {
        val response = OtaResponse.parse("OK\n")
        assertTrue(response is OtaResponse.Ok)
        assertEquals(null, response.hwVersion)
    }

    @Test
    fun parseOkWithVersionData() {
        val response = OtaResponse.parse("OK 1 2.3.4 45 v2.3.4-abc123\n")
        assertTrue(response is OtaResponse.Ok)

        // Asserting the values parsed correctly
        assertEquals("1", response.hwVersion)
        assertEquals("2.3.4", response.fwVersion)
        assertEquals(45, response.rebootCount)
        assertEquals("v2.3.4-abc123", response.gitHash)
    }

    @Test
    fun parseErasing() {
        val response = OtaResponse.parse("ERASING\n")
        assertTrue(response is OtaResponse.Erasing)
    }

    @Test
    fun parseAck() {
        val response = OtaResponse.parse("ACK\n")
        assertTrue(response is OtaResponse.Ack)
    }

    @Test
    fun parseErrorWithMessage() {
        val response = OtaResponse.parse("ERR Hash Rejected\n")
        assertTrue(response is OtaResponse.Error)
        assertEquals("Hash Rejected", response.message)
    }

    @Test
    fun parseSimpleError() {
        val response = OtaResponse.parse("ERR\n")
        assertTrue(response is OtaResponse.Error)
        assertEquals("Unknown error", response.message)
    }

    @Test
    fun parseUnknownResponse() {
        val response = OtaResponse.parse("SOMETHING_ELSE\n")
        assertTrue(response is OtaResponse.Error)
        assertTrue(response.message.startsWith("Unknown response"))
    }
}
