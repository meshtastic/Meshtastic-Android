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
package org.meshtastic.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks down the tolerant "payload" decoding: string, nested object, and null/absent must all parse. */
class MqttJsonPayloadTest {

    // Matches the decode-relevant MQTTRepositoryImpl config.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun string_payload_decodes_unchanged() {
        val jsonStr =
            """{"type":"text","from":12345678,"to":4294967295,"payload":"Hello World","hop_limit":3,"id":123}"""
        val decoded = json.decodeFromString<MqttJsonPayload>(jsonStr)

        assertEquals("text", decoded.type)
        assertEquals(12345678L, decoded.from)
        assertEquals("Hello World", decoded.payload)
    }

    @Test
    fun object_payload_decodes_to_compact_json_text() {
        val jsonStr =
            """{"type":"position","from":12345678,"payload":{"latitude_i":123456789,"longitude_i":-987654321,"time":1600000000}}"""
        val decoded = json.decodeFromString<MqttJsonPayload>(jsonStr)

        assertEquals("position", decoded.type)
        assertEquals("""{"latitude_i":123456789,"longitude_i":-987654321,"time":1600000000}""", decoded.payload)
    }

    @Test
    fun null_payload_decodes_to_null() {
        val decoded = json.decodeFromString<MqttJsonPayload>("""{"type":"text","from":1,"payload":null}""")
        assertNull(decoded.payload)
    }

    @Test
    fun absent_payload_decodes_to_null() {
        val decoded = json.decodeFromString<MqttJsonPayload>("""{"type":"text","from":1}""")
        assertNull(decoded.payload)
    }

    @Test
    fun array_payload_decodes_to_compact_json_text() {
        val decoded = json.decodeFromString<MqttJsonPayload>("""{"type":"text","from":1,"payload":[1,2]}""")
        assertEquals("[1,2]", decoded.payload)
    }

    @Test
    fun non_string_primitive_payload_is_coerced_to_text() {
        val decoded = json.decodeFromString<MqttJsonPayload>("""{"type":"text","from":1,"payload":42}""")
        assertEquals("42", decoded.payload)
    }

    @Test
    fun string_payload_round_trips_as_string() {
        val encoded =
            json.encodeToString(MqttJsonPayload.serializer(), MqttJsonPayload(type = "text", from = 1, payload = "hi"))
        assertTrue(encoded.contains(""""payload":"hi""""))
    }

    @Test
    fun garbage_input_still_fails() {
        assertFailsWith<SerializationException> { json.decodeFromString<MqttJsonPayload>("""{"from":"not json""") }
    }
}
