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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MqttEndpointSupportWebTest {
    @Test
    fun `a browser opens a WebSocket URL but not a bare host`() {
        assertFalse(resolveEndpoint("mqtt.meshtastic.org", tlsEnabled = false).isOpenableHere())
        assertFalse(resolveEndpoint("mqtt.meshtastic.org", tlsEnabled = true).isOpenableHere())
        assertTrue(resolveEndpoint("wss://broker.example.com/mqtt", tlsEnabled = true).isOpenableHere())
        assertTrue(resolveEndpoint("ws://192.168.1.20:8080/mqtt", tlsEnabled = false).isOpenableHere())
    }
}
