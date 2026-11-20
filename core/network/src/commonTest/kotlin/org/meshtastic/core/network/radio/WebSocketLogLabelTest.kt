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
package org.meshtastic.core.network.radio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebSocketLogLabelTest {

    @Test
    fun `a private host keeps its scheme host and port and nothing after them`() {
        assertEquals("ws://192.168.1.50:80", "ws://192.168.1.50/proxy-secret/api/v1/ws?token=abc".toLogLabel())
        assertEquals("wss://[fd00::1]:9443", "wss://[fd00::1]:9443/api/v1/ws".toLogLabel())
    }

    @Test
    fun `a public host is anonymized and its path and query never reach the label`() {
        val label = "wss://mesh.example.org:9443/proxy-secret/api/v1/ws?token=abc".toLogLabel()
        assertFalse("mesh.example.org" in label, label)
        assertFalse("proxy-secret" in label, label)
        assertFalse("token" in label, label)
        assertFalse("/api/v1/ws" in label, label)
    }
}
