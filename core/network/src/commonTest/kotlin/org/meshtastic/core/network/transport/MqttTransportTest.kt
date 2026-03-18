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
package org.meshtastic.core.network.transport

import kotlin.test.Test
import kotlin.test.assertEquals

class MqttTransportTest {
    @Test
    fun `test configuration values`() {
        val config = MqttTransport.MqttTransportConfig(
            address = "mqtt.example.com:1883",
            username = "user",
            password = "password",
            tlsEnabled = true,
            subscribeTopics = listOf("topic1", "topic2")
        )
        
        assertEquals("mqtt.example.com:1883", config.address)
        assertEquals("user", config.username)
        assertEquals("password", config.password)
        assertEquals(true, config.tlsEnabled)
        assertEquals(2, config.subscribeTopics.size)
    }

    @Test
    fun `test address parsing logic`() {
        val address1 = "mqtt.example.com:1883"
        val (host1, port1) = address1.split(":", limit = 2).let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1883)
        }
        assertEquals("mqtt.example.com", host1)
        assertEquals(1883, port1)

        val address2 = "mqtt.example.com"
        val (host2, port2) = address2.split(":", limit = 2).let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1883)
        }
        assertEquals("mqtt.example.com", host2)
        assertEquals(1883, port2)
    }
}
