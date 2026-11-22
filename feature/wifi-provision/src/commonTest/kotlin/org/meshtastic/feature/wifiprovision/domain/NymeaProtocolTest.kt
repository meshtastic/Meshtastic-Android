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
package org.meshtastic.feature.wifiprovision.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests for the nymea JSON protocol serialization models. */
class NymeaProtocolTest {

    // -----------------------------------------------------------------------
    // NymeaSimpleCommand
    // -----------------------------------------------------------------------

    @Test
    fun `simple command serializes to compact JSON`() {
        val json = NymeaJson.encodeToString(NymeaSimpleCommand(command = 4))
        assertEquals("""{"c":4}""", json)
    }

    @Test
    fun `simple command round-trips`() {
        val original = NymeaSimpleCommand(command = 0)
        val json = NymeaJson.encodeToString(original)
        val decoded = NymeaJson.decodeFromString<NymeaSimpleCommand>(json)
        assertEquals(original, decoded)
    }

    // -----------------------------------------------------------------------
    // NymeaConnectCommand
    // -----------------------------------------------------------------------

    @Test
    fun `connect command serializes with nested params`() {
        val cmd = NymeaConnectCommand(command = 1, params = NymeaConnectParams(ssid = "TestNet", password = "pass123"))
        val json = NymeaJson.encodeToString(cmd)
        assertTrue(json.contains("\"c\":1"))
        assertTrue(json.contains("\"e\":\"TestNet\""))
        assertTrue(json.contains("\"p\":\"pass123\""))
    }

    @Test
    fun `connect command with empty password`() {
        val cmd = NymeaConnectCommand(command = 1, params = NymeaConnectParams(ssid = "OpenNet", password = ""))
        val json = NymeaJson.encodeToString(cmd)
        assertTrue(json.contains("\"p\":\"\""))
    }

    @Test
    fun `connect command round-trips`() {
        val original =
            NymeaConnectCommand(command = 2, params = NymeaConnectParams(ssid = "Hidden", password = "secret"))
        val json = NymeaJson.encodeToString(original)
        val decoded = NymeaJson.decodeFromString<NymeaConnectCommand>(json)
        assertEquals(original, decoded)
    }

    // -----------------------------------------------------------------------
    // NymeaResponse
    // -----------------------------------------------------------------------

    @Test
    fun `response deserializes success`() {
        val response = NymeaJson.decodeFromString<NymeaResponse>("""{"c":4,"r":0}""")
        assertEquals(4, response.command)
        assertEquals(0, response.responseCode)
        assertEquals(null, response.connectionInfo)
    }

    @Test
    fun `response deserializes error code`() {
        val response = NymeaJson.decodeFromString<NymeaResponse>("""{"c":1,"r":3}""")
        assertEquals(1, response.command)
        assertEquals(3, response.responseCode)
    }

    @Test
    fun `response deserializes connection info payload`() {
        val response = NymeaJson.decodeFromString<NymeaResponse>("""{"c":5,"r":0,"p":{"i":"10.10.10.61"}}""")
        assertEquals(5, response.command)
        assertEquals(0, response.responseCode)
        assertEquals("10.10.10.61", response.connectionInfo?.ipAddress)
    }

    @Test
    fun `response ignores unknown keys`() {
        val response = NymeaJson.decodeFromString<NymeaResponse>("""{"c":0,"r":0,"extra":"field"}""")
        assertEquals(0, response.responseCode)
    }

    // -----------------------------------------------------------------------
    // NymeaNetworksResponse
    // -----------------------------------------------------------------------

    @Test
    fun `networks response deserializes network list`() {
        val json =
            """
            {
                "c": 0,
                "r": 0,
                "p": [
                    {"e":"HomeWifi","m":"AA:BB:CC:DD:EE:01","s":85,"p":1},
                    {"e":"OpenNet","m":"AA:BB:CC:DD:EE:02","s":60,"p":0}
                ]
            }
            """
                .trimIndent()
        val response = NymeaJson.decodeFromString<NymeaNetworksResponse>(json)
        assertEquals(0, response.responseCode)
        val networks = requireNotNull(response.networks)
        assertEquals(2, networks.size)
        assertEquals("HomeWifi", networks[0].ssid)
        assertEquals(85, networks[0].signalStrength)
        assertEquals(1, networks[0].protection)
        assertEquals("OpenNet", networks[1].ssid)
        assertEquals(0, networks[1].protection)
    }

    @Test
    fun `networks response deserializes empty list`() {
        val json = """{"c":0,"r":0,"p":[]}"""
        val response = NymeaJson.decodeFromString<NymeaNetworksResponse>(json)
        assertTrue(requireNotNull(response.networks).isEmpty())
    }

    @Test
    fun `networks response keeps a missing bssid empty`() {
        val json = """{"c":0,"r":0,"p":[{"e":"Minimal","s":40,"p":1}]}"""
        val entry = requireNotNull(NymeaJson.decodeFromString<NymeaNetworksResponse>(json).networks)[0]
        assertEquals("Minimal", entry.ssid)
        assertEquals("", entry.bssid)
    }

    @Test
    fun `networks response without a list has no list rather than an empty one`() {
        val response = NymeaJson.decodeFromString<NymeaNetworksResponse>("""{"c":0,"r":0}""")
        assertEquals(null, response.networks)
    }

    @Test
    fun `a network without a signal or protection does not decode`() {
        assertFailsWith<SerializationException> {
            NymeaJson.decodeFromString<NymeaNetworksResponse>("""{"c":0,"r":0,"p":[{"e":"Minimal"}]}""")
        }
    }

    @Test
    fun `a network without a protection flag does not decode`() {
        assertFailsWith<SerializationException> {
            NymeaJson.decodeFromString<NymeaNetworksResponse>("""{"c":0,"r":0,"p":[{"e":"Minimal","s":70}]}""")
        }
    }

    @Test
    fun `a network without a signal does not decode`() {
        assertFailsWith<SerializationException> {
            NymeaJson.decodeFromString<NymeaNetworksResponse>("""{"c":0,"r":0,"p":[{"e":"Minimal","p":1}]}""")
        }
    }

    @Test
    fun `a response without a status does not decode`() {
        assertFailsWith<SerializationException> { NymeaJson.decodeFromString<NymeaResponse>("""{"c":1}""") }
    }

    @Test
    fun `a response with a quoted status does not decode`() {
        assertFailsWith<SerializationException> { NymeaJson.decodeFromString<NymeaResponse>("""{"c":1,"r":"0"}""") }
    }

    @Test
    fun `a response with a null status does not decode`() {
        assertFailsWith<SerializationException> { NymeaJson.decodeFromString<NymeaResponse>("""{"c":1,"r":null}""") }
    }

    @Test
    fun `a reply header without a command does not decode`() {
        assertFailsWith<SerializationException> { NymeaJson.decodeFromString<NymeaReplyHeader>("""{"r":0}""") }
    }
}
