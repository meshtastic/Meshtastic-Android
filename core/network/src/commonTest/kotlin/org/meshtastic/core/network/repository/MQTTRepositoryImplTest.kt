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
package org.meshtastic.core.network.repository

import kotlinx.serialization.json.Json
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.mqtt.MqttEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MQTTRepositoryImplTest {

    // region resolveEndpoint — every behavioral branch of address parsing.

    @Test
    fun `bare host without scheme is wrapped as ws WebSocket on the standard port`() {
        val endpoint = resolveEndpoint(rawAddress = "broker.example.com", tlsEnabled = false)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("ws://broker.example.com/mqtt", ws.url)
    }

    @Test
    fun `bare host with TLS enabled is upgraded to wss`() {
        val endpoint = resolveEndpoint(rawAddress = "broker.example.com", tlsEnabled = true)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("wss://broker.example.com/mqtt", ws.url)
    }

    @Test
    fun `host with explicit port is preserved when wrapped`() {
        val endpoint = resolveEndpoint(rawAddress = "broker.example.com:9001", tlsEnabled = false)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("ws://broker.example.com:9001/mqtt", ws.url)
    }

    @Test
    fun `address with ws scheme is parsed as-is and tls flag is ignored`() {
        // tlsEnabled is intentionally true here — when the user supplies a full URL we
        // must honor whatever scheme they provided, not silently upgrade it.
        val endpoint = resolveEndpoint(rawAddress = "ws://broker.example.com:8080/custom-path", tlsEnabled = true)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("ws://broker.example.com:8080/custom-path", ws.url)
    }

    @Test
    fun `address with wss scheme is parsed as-is`() {
        val endpoint = resolveEndpoint(rawAddress = "wss://broker.example.com/secure-mqtt", tlsEnabled = false)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("wss://broker.example.com/secure-mqtt", ws.url)
    }

    @Test
    fun `address with mqtt tcp scheme is parsed as Tcp endpoint`() {
        val endpoint = resolveEndpoint(rawAddress = "mqtt://broker.example.com:1883", tlsEnabled = false)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(1883, tcp.port)
        assertEquals(false, tcp.tls)
    }

    @Test
    fun `address with mqtts tcp scheme is parsed as Tcp endpoint with tls true`() {
        val endpoint = resolveEndpoint(rawAddress = "mqtts://broker.example.com:8883", tlsEnabled = false)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(8883, tcp.port)
        assertEquals(true, tcp.tls)
    }

    // endregion

    // region MqttJsonPayload — keep the existing JSON contract tests.

    @Test
    fun `test json payload parsing`() {
        val jsonStr =
            """{"type":"text","from":12345678,"to":4294967295,"payload":"Hello World","hop_limit":3,"id":123,"time":1600000000}"""
        val json = Json { ignoreUnknownKeys = true }
        val payload = json.decodeFromString<MqttJsonPayload>(jsonStr)

        assertEquals("text", payload.type)
        assertEquals(12345678L, payload.from)
        assertEquals(4294967295L, payload.to)
        assertEquals("Hello World", payload.payload)
        assertEquals(3, payload.hopLimit)
        assertEquals(123L, payload.id)
        assertEquals(1600000000L, payload.time)
    }

    @Test
    fun `test json payload serialization`() {
        val payload =
            MqttJsonPayload(
                type = "text",
                from = 12345678,
                to = 4294967295,
                payload = "Hello World",
                hopLimit = 3,
                id = 123,
                time = 1600000000,
            )
        val json = Json { ignoreUnknownKeys = true }
        val jsonStr = json.encodeToString(MqttJsonPayload.serializer(), payload)

        assertTrue(jsonStr.contains("\"type\":\"text\""))
        assertTrue(jsonStr.contains("\"from\":12345678"))
        assertTrue(jsonStr.contains("\"payload\":\"Hello World\""))
    }

    // endregion
}
